# Velar DNS

DNS criptografado (DNS-over-HTTPS) + proteção contra ameaças para Android TV, Google TV e TV Box.

O app cria uma VPN **local** que captura só as consultas DNS e as envia por HTTPS (porta 443) para o servidor escolhido — Cloudflare, Google, Quad9, AdGuard ou um endereço personalizado. O restante da internet não passa pelo app.

## Proteção contra ameaças

O Velar DNS bloqueia domínios conhecidos como maliciosos antes mesmo de resolver a consulta DNS. Se um domínio está na lista de bloqueio, o app retorna `0.0.0.0` — o dispositivo nunca chega a se conectar à ameaça.

**Fontes das listas (públicas, gratuitas, atualizadas pela comunidade):**

- **Steven Black** — ~170 mil domínios de malware e phishing
- **URLhaus (abuse.ch)** — distribuição de malware
- **Phishing Army** — phishing confirmado

As listas são baixadas automaticamente a cada 24 horas. A proteção vem ativada por padrão e pode ser desligada na tela "Proteção".

**Whitelist:** se um domínio legítimo for bloqueado por engano, você pode adicioná-lo à whitelist na tela de Proteção.

## Como gerar o APK (GitHub Actions)

1. Crie um repositório no GitHub e envie esta pasta inteira (incluindo `.github/`).
2. A cada `push` na branch `main`, o workflow **Gerar APK** compila o app.
3. Abra a aba **Actions** → execução mais recente → baixe o artefato `velar-dns-apk`.

Também dá para rodar manualmente: **Actions → Gerar APK → Run workflow**.

### Publicar uma versão em Releases

Jeito mais simples: edite o arquivo `version.txt` (ex.: troque `1.0.0` por `1.1.0`) e, se quiser, o texto de `NOVIDADES.md`. Ao salvar na branch `main`, o workflow compila, assina e cria a Release `v1.1.0` com o arquivo `velar-dns.apk`. Envios para a `main` sem mudar a versão só geram o APK em Actions, sem nova Release.

Também funciona criar pelo site (**Releases → Draft a new release → tag `v1.1.0` → Publish release**): o workflow anexa o APK em uns 3 minutos.

Link fixo da versão mais nova (bom para enviar a clientes):
`https://github.com/velar-labs/velar-dns/releases/latest/download/velar-dns.apk`

### Assinatura (importante para atualizações)

Sem configurar uma chave, o APK sai assinado com uma chave de debug que **muda a cada execução**. Ele instala, mas a próxima versão não instala por cima — o cliente teria que desinstalar antes.

Para fixar a chave, gere uma vez no seu computador:

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

Guarde o `velar.jks` e as senhas em lugar seguro: perdeu a chave, não dá mais para atualizar o app instalado.

## Compilar no computador

Android Studio (Ladybug ou mais novo) → **Open** nesta pasta → **Build → Build APK(s)**. Requer JDK 17.

## Como funciona

- `vpn/VelarVpnService.kt` — VPN local: endereço virtual `10.111.222.2`, DNS virtual `10.111.222.1`. Só a rota do DNS virtual (e, com "Forçar todo DNS", a de DNS públicos conhecidos) entra no túnel.
- `dns/Packets.kt` — lê pacotes IPv4/UDP da porta 53 e monta as respostas.
- `dns/Doh.kt` — cliente DoH (RFC 8484) com IPs fixos de cada servidor, para não depender do DNS da rede. Troca automática de servidor depois de 3 falhas seguidas. Quando a proteção está ativa, verifica cada domínio contra as listas de bloqueio antes de enviar a consulta.
- `filter/` — motor de bloqueio por DNS: `DomainMatcher` (HashSet O(1) com subdomain matching), `BlocklistManager` (download, cache e merge das listas), `BlockLog` (log em memória dos últimos 200 bloqueios), `BlocklistUpdater` (atualização automática a cada 24h).
- `diag/Diagnostics.kt` — testes da tela Diagnóstico.
- `ui/` — telas em Jetpack Compose, navegáveis pelo controle remoto.

## Observações

- Se a TV mostrar "DNS privado não pode ser acessado", desligue o **DNS privado** nas configurações de rede do Android.
- Para máxima estabilidade, marque o Velar DNS como **VPN sempre ativa** (Ajustes → "VPN sempre ativa").
- Só um app de VPN funciona por vez no Android. Ativar outra VPN desliga o Velar DNS.
- A religação automática após reiniciar depende da permissão de VPN continuar válida e das regras do firmware da TV Box.
- A proteção usa ~10-15 MB de memória para as listas de domínios — aceitável para a maioria das TV Boxes.

---

Desenvolvido por **Velar Labs**.
