Velar DNS
Encrypted DNS (DNS-over-HTTPS) + threat protection for Android TV, Google TV, and TV Box.

The app creates a local VPN that intercepts only DNS queries and sends them via HTTPS (port 443) to your chosen server—Cloudflare, Google, Quad9, AdGuard, or a custom address. The rest of your internet traffic does not pass through the app.

Threat Protection
Velar DNS blocks known malicious domains before the DNS query is even resolved. If a domain is on the blocklist, the app returns 0.0.0.0—ensuring your device never connects to the threat.

List sources (public, free, and community-maintained):

Steven Black — ~170,000 malware and phishing domains
Phishing Army — confirmed phishing sites
The lists are automatically downloaded every 24 hours. Protection is enabled by default and can be toggled off on the "Protection" screen.

Whitelist: If a legitimate domain is blocked by mistake, you can add it to the whitelist via the "Protection" screen.

How to generate the APK (GitHub Actions)
Create a GitHub repository and upload this entire folder (including the .github/ directory).
Every time you push to the `main` branch, the "Generate APK" workflow will compile the app.
Go to the "Actions" tab, select the most recent run, and download the `velar-dns-apk` artifact.
You can also trigger it manually: Actions → Generate APK → Run workflow.

Publishing a release
The simplest method: edit the `version.txt` file (e.g., change 1.0.0 to 1.1.0) and, optionally, update the `NOVIDADES.md` (What's New) file. When you push to the `main` branch, the workflow compiles, signs, and creates a "Release v1.1.0" containing the `velar-dns.apk` file. Pushes to `main` that do not change the version will only generate an APK within the "Actions" tab, without creating a new release.

You can also create a release via the website (Releases → Draft a new release → tag `v1.1.0` → Publish release); the workflow will attach the APK within about 3 minutes.

Permanent link for the latest version (useful for sharing with clients): https://github.com/velar-labs/velar-dns/releases/latest/download/velar-dns.apk

Signing (important for updates)
If no key is configured, the APK will be signed with a debug key that changes with every build. While it can be installed, subsequent versions cannot be installed over it—the user would have to uninstall the existing app first.

To set a fixed signing key, generate one on your computer:

keytool -genkeypair -v -keystore velar.jks -alias velar -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 velar.jks > velar.jks.b64
Then, add the following to Settings → Secrets and variables → Actions:

Secret	Value
VELAR_KEYSTORE_BASE64	contents of `velar.jks.b64`
VELAR_KEYSTORE_PASSWORD	keystore password
VELAR_KEY_ALIAS	`velar`
VELAR_KEY_PASSWORD	key password
Keep the `velar.jks` file and the passwords in a secure place; if the key is lost, it will no longer be possible to update the installed app.

Compiling on your computer
Android Studio (Ladybug or newer) → Open this folder → Build → Build APK(s). Requires JDK 17.

How it works
vpn/VelarVpnService.kt — Local VPN: virtual address 10.111.222.2, virtual DNS 10.111.222.1. Only the route for the virtual DNS (and, if "Force all DNS" is enabled, routes for known public DNS servers) enters the tunnel.
dns/Packets.kt — Reads IPv4/UDP packets from port 53 and constructs the responses.
dns/DoH.kt — DoH (RFC 8484) client using fixed IP addresses for each server, ensuring independence from the network's DNS. Automatically switches servers after 3 consecutive failures. When protection is active, it checks each domain against blocklists before sending the query.
filter/ — DNS-based blocking engine (v1.3: BlockStats keeps 7-day and top-domain stats; dns/DnsCache keeps DoH answers in memory): DomainMatcher (O(1) HashSet with subdomain matching), BlocklistManager (handles downloading, caching, and merging lists), BlockLog (in-memory log of the last 200 blocked requests), and BlocklistUpdater (automatic updates every 24 hours).
diag/Diagnostics.kt — Tests for the Diagnostics screen.
ui/ — Screens built with Jetpack Compose, navigable via remote control.
Notes
If the TV displays "Private DNS cannot be accessed," disable Private DNS in the Android network settings.
For maximum stability, set Velar DNS as an "always-on" VPN (Settings → "Always-on VPN").
Only one VPN app can be active at a time on Android. Enabling another VPN will disable Velar DNS.
Automatic reconnection after a reboot depends on the VPN permission remaining valid and the specific firmware rules of the TV Box.
The protection feature uses approximately 10–15 MB of memory for domain lists—an acceptable amount for most TV Boxes.# Velar DNS

Portugues
DNS criptografado (DNS-over-HTTPS) + proteção contra ameaças para Android TV, Google TV e TV Box.

O app cria uma VPN **local** que captura só as consultas DNS e as envia por HTTPS (porta 443) para o servidor escolhido — Cloudflare, Google, Quad9, AdGuard ou um endereço personalizado. O restante da internet não passa pelo app.

## Proteção contra ameaças

O Velar DNS bloqueia domínios conhecidos como maliciosos antes mesmo de resolver a consulta DNS. Se um domínio está na lista de bloqueio, o app retorna `0.0.0.0` — o dispositivo nunca chega a se conectar à ameaça.

**Fontes das listas (públicas, gratuitas, atualizadas pela comunidade):**

- **Steven Black** — ~170 mil domínios de malware e phishing
- **Phishing Army** — phishing confirmado

As listas são baixadas automaticamente a cada 24 horas. A proteção vem ativada por padrão e pode ser desligada na tela "Proteção".

**IPv6:** domínios bloqueados também recebem resposta de bloqueio para consultas AAAA (`::`), então não escapam pelo IPv6.

**Estatísticas:** a tela Proteção mostra os bloqueios dos últimos 7 dias e os domínios mais bloqueados, guardados só no aparelho.

**Cache DNS:** respostas ficam em memória pelo tempo de validade (TTL, no máximo 10 min), para abrir sites mais rápido.

**Whitelist:** se um domínio legítimo for bloqueado por engano, aperte OK sobre ele em "Bloqueios recentes", na tela Proteção. Os liberados aparecem numa lista, e OK nela volta a bloquear.

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
- **Contador de aparelhos (desde 1.2.0):** cada instalação gera um código aleatório e, com a proteção ligada, avisa um banco Supabase a cada 30 min (`stats/DeviceCounter.kt`). Não envia nome, conta, sites visitados nem consultas DNS. Totais: função `stats()` no projeto Supabase `velar-dns`.

---

Desenvolvido por **Velar Labs**.
