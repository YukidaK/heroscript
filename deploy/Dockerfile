# O site/ chega pronto (docs MkDocs + playground, gerados por scripts/build_site.sh):
# nao ha etapa de build aqui, so copiar pro nginx.
# O contexto e a raiz do repo (ver build-image.sh), por isso os caminhos deploy/ e site/.
FROM nginx:alpine

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html

EXPOSE 80
