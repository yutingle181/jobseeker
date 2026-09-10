# 多阶段构建：Node 构建 → Nginx 托管静态资源
FROM node:20-alpine AS builder
WORKDIR /build
COPY package.json .
RUN npm install --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
