# Releasing / Publicar versões

> Guia para quem mantém o projeto. *Maintainer guide (Portuguese).*

## Gerar o APK (GitHub Actions)

A cada `push` na branch `main`, o workflow **Gerar APK** (`.github/workflows/build.yml`) compila o app.

- Abra a aba **Actions** → execução mais recente → baixe o artefato `velar-dns-apk`.
- Para rodar manualmente: **Actions → Gerar APK → Run workflow**.

## Publicar uma versão em Releases

**Jeito mais simples:** edite `version.txt` (ex.: `1.3.2` → `1.4.0`) e, se quiser, o texto de `NOVIDADES.md`. Ao enviar para a `main`, o workflow compila, assina e cria a Release `v1.4.0` com o arquivo `velar-dns.apk`, usando `NOVIDADES.md` como notas.

Envios para a `main` sem mudar a versão só geram o APK em Actions, sem nova Release.

**Pelo site:** **Releases → Draft a new release → tag `v1.4.0` → Publish release**. O workflow anexa o APK em uns 3 minutos.

Link fixo da versão mais nova:

```
https://github.com/velar-labs/velar-dns/releases/latest/download/velar-dns.apk
```

> A Release só é publicada se o APK estiver assinado com a chave fixa. Sem a chave, o APK fica só em Actions.

## Assinatura (importante para atualizações)

Sem chave configurada, o APK sai assinado com uma chave de debug que **muda a cada execução**: ele instala, mas a versão seguinte não instala por cima — seria preciso desinstalar antes.

Para fixar a chave, gere uma vez no computador:

```bash
keytool -genkeypair -v -keystore velar.jks -alias velar -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 velar.jks > velar.jks.b64
```

E cadastre em **Settings → Secrets and variables → Actions**:

| Secret | Valor |
|---|---|
| `VELAR_KEYSTORE_BASE64` | conteúdo de `velar.jks.b64` |
| `VELAR_KEYSTORE_PASSWORD` | senha do keystore |
| `VELAR_KEY_ALIAS` | `velar` |
| `VELAR_KEY_PASSWORD` | senha da chave |

Guarde o `velar.jks` e as senhas em lugar seguro: **perdeu a chave, não dá mais para atualizar o app instalado.**

O workflow confere a impressão digital do arquivo da chave e do certificado do APK antes de publicar. Se trocar a chave, atualize `EXPECTED_JKS_SHA256` e `EXPECTED` em `.github/workflows/build.yml`.

## Contador de aparelhos

`stats/DeviceCounter.kt` envia um código aleatório e a versão do app ao projeto Supabase `velar-dns` a cada 30 min, com a proteção ligada. Totais: função `stats()` nesse projeto.
