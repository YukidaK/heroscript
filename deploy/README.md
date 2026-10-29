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
| `Dockerfile`, `nginx.conf` | nginx servindo `site/`, com `Cache-Control: no-cache` e Range requests (exigidas pelo CheerpJ) |
| `docker-compose.yml` | site + `cloudflared`, sem porta exposta |
| `build-image.sh` | builda a imagem no host pela Docker API do Portainer |
| `terraform/` | tunnel, CNAME, build e stack no Portainer |
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

Para só testar a imagem: `docker build -f deploy/Dockerfile -t heroscript . && docker run --rm -p 8080:80 heroscript`.

Não existe `cloudflare_ruleset` aqui de propósito: a fase de cache da zona pertence ao `tf-parque-palmeiras`
(uma zona só tem um ruleset por fase).
