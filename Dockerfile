FROM node:20-alpine

WORKDIR /usr/src/hollow-web

COPY web/package*.json ./
RUN npm install --omit=dev

COPY web/ ./

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

CMD ["node", "server.js"]
