FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG API_URL=http://localhost:8082
ARG AUTH_API_URL=http://localhost:8083
RUN sed -i "s|apiUrl: '.*'|apiUrl: '${API_URL}'|" src/environments/environment.ts \
    && sed -i "s|authApiUrl: '.*'|authApiUrl: '${AUTH_API_URL}'|" src/environments/environment.ts \
    && npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]