# Repository instructions

## Backend API routes

Use `backend/src/main/java/com/neueda/leap/ApiRoutes.java` as the source of truth for backend route path constants. When adding or changing an endpoint, define or update its path there and reuse the constants in controller mapping annotations and `SecurityConfig` request matchers. Keep authorization rules aligned with route changes. If an existing endpoint still uses a path literal, move it to `ApiRoutes.java` when editing that endpoint.
