# Deploy do site

O site (documentação MkDocs + playground) vai ao ar em **https://heroscript.viniciussc.com** no mesmo esquema
dos outros stacks (`football-pca-map`, `tf-parque-palmeiras`): imagem nginx buildada no host pelo Portainer,
stack com sidecar `cloudflared` e tunnel Cloudflare próprio, state no MinIO. O repositório pode ser privado:
nada depende do GitHub Pages.

Quem aplica é o workflow [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml):

| Job | Quando | O que faz |
|---|---|---|
| `build` | sempre | `scripts/build_site.sh` (testes → playground → `mkdocs build --strict`) e guarda `site/` como artefato |
| `validate` | sempre | `terraform fmt`/`validate`, sem segredo |
| `apply` | push na `main` | baixa o `site/` e roda `terraform apply` com os segredos do environment `production` |

| Arquivo | O que faz |
|---|---|
| `Dockerfile`, `nginx.conf` | nginx servindo `site/`, com `Cache-Control: no-cache` e Range requests (exigidas pelo CheerpJ), e repassando `/api/` ao runner |
| `Dockerfile.runner` | JDK + `heroscript.server.RunnerServer`: executa os programas do playground |
| `docker-compose.yml` | site + runner + `cloudflared`, sem porta exposta |
| `docker-compose.local.yml` | site + runner para teste local, publicado só em `127.0.0.1` |
| `build-image.sh` | builda uma imagem no host pela Docker API do Portainer (`CONTEXT_PATHS` escolhe o que enviar) |
| `terraform/` | tunnel, CNAME, build das duas imagens e stack no Portainer |
| `set-github-secrets.sh` | sobe os secrets para o GitHub Environment a partir do seu `terraform.tfvars` local |

## Segredos: só no GitHub Environment

Nenhuma credencial fica no repositório. Só o job `apply` usa o environment **`production`**, que guarda:

`MINIO_ACCESS_KEY_ID`, `MINIO_SECRET_ACCESS_KEY`, `PORTAINER_ENDPOINT`, `PORTAINER_USER`,
`PORTAINER_PASSWORD`, `CLOUDFLARE_API_KEY`, `CLOUDFLARE_EMAIL`, `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_ZONE_ID`.

### Configuração (uma vez, por alguém com admin no repo)

1. **Settings → Environments → New environment → `production`**, em *Deployment branches and tags* →
   **Selected branches** → só `main`.
2. Secrets: pela interface ou com o script, que lê `terraform/terraform.tfvars` (no `.gitignore`):
   ```bash
   cp deploy/terraform/terraform.tfvars.example deploy/terraform/terraform.tfvars   # e preencha
   AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... ./deploy/set-github-secrets.sh
   ```

## Rodar local

```bash
scripts/build_site.sh                         # gera site/
cd deploy/terraform
terraform init -backend-config=backend.hcl   # precisa de AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY (MinIO)
terraform plan
```

Para testar o site com o runner, sem túnel:

```bash
scripts/build_site.sh
docker build -f deploy/Dockerfile -t heroscript .
docker build -f deploy/Dockerfile.runner -t heroscript-runner .
docker compose -f deploy/docker-compose.local.yml up      # http://127.0.0.1:8080/playground/
```

## O runner

O container `runner` executa Java arbitrário enviado por visitantes (ver
[Playground](../docs/como-funciona/playground.md)). O que o contém está no `docker-compose.yml`:

- **Rede**: só a `runner-network`, `internal: true`. Sem internet, sem LAN, sem os outros stacks; só o nginx o alcança.
- **Disco**: `read_only`, com `/tmp` em `tmpfs` de 64 MB.
- **Privilégios**: usuário `nobody`, `cap_drop: ALL`, `no-new-privileges`.
- **Recursos**: `pids_limit: 256`, 1 GB de memória, 1,5 CPU.

Não adicione a ele `ports`, volumes, o socket do Docker, outra rede ou variáveis com segredos. O nginx resolve
`runner` a cada requisição, então o site sobe e funciona (executando no navegador) mesmo com o runner parado.

Ajustes por variável de ambiente no serviço: `RUNNER_CONCURRENCY` (2), `RUNNER_TIMEOUT_SECONDS` (10),
`RUNNER_MAX_SOURCE_BYTES`, `RUNNER_MAX_OUTPUT_BYTES`, `RUNNER_CHILD_MEMORY` (`128m`).

Não existe `cloudflare_ruleset` aqui de propósito: a fase de cache da zona pertence ao `tf-parque-palmeiras`
(uma zona só tem um ruleset por fase).
