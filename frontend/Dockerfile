FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --ignore-scripts

COPY angular.json ./
COPY tsconfig.json ./
COPY tsconfig.spec.json ./
COPY karma.conf.js ./
COPY src ./src
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/autocomplete-frontend/ /usr/share/nginx/html

EXPOSE 8080

