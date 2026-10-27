FROM node:20-alpine

WORKDIR /usr/src/hollow

# Install web server dependencies
COPY web/package*.json ./web/
RUN cd web && npm install --omit=dev

# Copy web server and server-side dynamic modules repository
COPY web/ ./web/
COPY modules/ ./modules/

WORKDIR /usr/src/hollow/web

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

CMD ["node", "server.js"]
