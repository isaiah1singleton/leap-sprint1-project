import { spawn, execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { existsSync } from "node:fs";
import { setTimeout } from "node:timers/promises";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const env = { ...process.env, CI: "true" };
let container;
let activeChild;

function cleanContainer() {
  if (!container) return;
  execFileSync("docker", ["rm", "--force", container], {
    stdio: "pipe",
    windowsHide: true,
  });
  container = undefined;
}
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    activeChild?.kill();
    try {
      cleanContainer();
    } catch (failure) {
      console.error(failure.message);
    }
    process.exit(signal === "SIGINT" ? 130 : 143);
  });
}

async function run(command, args, directory = root) {
  console.log(
    `\nRunning ${command} ${args.join(" ")} in ${path.relative(root, directory) || "."}`,
  );
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: directory,
      env,
      stdio: "inherit",
      shell: process.platform === "win32" && /^(npm|mvn)$/.test(command),
      windowsHide: true,
    });
    activeChild = child;
    child.once("error", reject);
    child.once("exit", (code) => {
      activeChild = undefined;
      if (code === 0) resolve();
      else reject(new Error(`${command} exited with ${code}`));
    });
  });
}
const npm = (args, directory) =>
  env.LEAP_NPM_CLI
    ? run(
        process.execPath,
        [env.LEAP_NPM_CLI, ...args],
        path.join(root, directory),
      )
    : run("npm", args, path.join(root, directory));

try {
  if (!env.LEAP_TEST_JDBC_URL) {
    container = `leap-suite-${randomUUID()}`;
    execFileSync(
      "docker",
      [
        "run",
        "--detach",
        "--rm",
        "--name",
        container,
        "-e",
        "POSTGRES_DB=leap_test",
        "-e",
        "POSTGRES_USER=leap_test",
        "-e",
        "POSTGRES_PASSWORD=test-only-password",
        "-p",
        "127.0.0.1::5432",
        "postgres:18",
      ],
      { stdio: "pipe", windowsHide: true },
    );
    let ready = false;
    for (let attempt = 0; attempt < 60; attempt++) {
      try {
        execFileSync(
          "docker",
          [
            "exec",
            container,
            "pg_isready",
            "-U",
            "leap_test",
            "-d",
            "leap_test",
          ],
          { stdio: "pipe", windowsHide: true },
        );
        ready = true;
        break;
      } catch {
        await setTimeout(1000);
      }
    }
    if (!ready)
      throw new Error("Disposable PostgreSQL failed to become ready.");
    const inspection = JSON.parse(
      execFileSync("docker", ["inspect", container], {
        encoding: "utf8",
        windowsHide: true,
      }),
    );
    const port = inspection[0].NetworkSettings.Ports["5432/tcp"][0].HostPort;
    env.LEAP_TEST_JDBC_URL = `jdbc:postgresql://127.0.0.1:${port}/leap_test`;
    env.LEAP_TEST_DB_USER = "leap_test";
    env.LEAP_TEST_DB_PASSWORD = "test-only-password";
  }
  await npm(["ci"], "auth");
  await npm(["ci"], "frontend");
  if (env.LEAP_SKIP_BROWSER_INSTALL !== "1") {
    await run(
      process.execPath,
      [
        path.join(root, "frontend/node_modules/@playwright/test/cli.js"),
        "install",
        ...(process.platform === "linux" ? ["--with-deps"] : []),
        "chromium",
      ],
      path.join(root, "frontend"),
    );
  }
  await run("mvn", ["-B", "verify"], path.join(root, "backend"));
  await npm(["run", "build"], "auth");
  await npm(["run", "test:ci"], "auth");
  await npm(["run", "test:ci"], "frontend");
  await npm(["run", "build"], "frontend");
  await npm(["run", "test:e2e"], "frontend");
  const python = path.join(
    root,
    ".venv",
    process.platform === "win32" ? "Scripts/python.exe" : "bin/python",
  );
  if (!existsSync(python))
    await run(
      env.LEAP_PYTHON ?? (process.platform === "win32" ? "python" : "python3"),
      ["-m", "venv", ".venv"],
    );
  await run(python, [
    "-m",
    "pip",
    "install",
    "--disable-pip-version-check",
    "-r",
    "analytics/proof/requirements.txt",
  ]);
  await run(python, [
    "-m",
    "unittest",
    "discover",
    "-s",
    "analytics/proof/tests",
  ]);
} catch (failure) {
  console.error(failure.message);
  process.exitCode = 1;
} finally {
  if (container) {
    try {
      cleanContainer();
    } catch (failure) {
      console.error(`Could not clean up ${container}: ${failure.message}`);
      process.exitCode = 1;
    }
  }
}
