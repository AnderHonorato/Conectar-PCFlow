#!/usr/bin/env bash
# Sobe o servidor .NET real e roda o cliente Android real contra ele.
# Prova o caminho completo: TLS + pinagem + handshake + comandos.
set -uo pipefail
cd "$(dirname "$0")/.."

DOTNET=${DOTNET:-/opt/dotnet/dotnet}
PORTA=${PORTA:-45456}
LOG=$(mktemp)

"$DOTNET" run --project tests/servidor/ServidorTeste.csproj -- "$PORTA" > "$LOG" 2>&1 &
SERVIDOR=$!
trap 'kill $SERVIDOR 2>/dev/null; wait $SERVIDOR 2>/dev/null' EXIT

# Espera a linha PRONTO com a impressão digital do certificado.
for _ in $(seq 1 60); do
  grep -q '^PRONTO ' "$LOG" && break
  sleep 1
done
if ! grep -q '^PRONTO ' "$LOG"; then
  echo "O servidor de teste não subiu:"; cat "$LOG"; exit 1
fi

TLS=$(grep -m1 '^PRONTO ' "$LOG" | sed 's/.*tls=//')
CODIGO=$(grep -m1 '^CODIGO ' "$LOG" | sed 's/^CODIGO //')
echo "Servidor de teste em 127.0.0.1:$PORTA"
echo "Impressao TLS: $TLS"
echo "Codigo de acesso: $CODIGO"
echo

export PCFLOW_TESTE_HOST=127.0.0.1
export PCFLOW_TESTE_PORTA="$PORTA"
export PCFLOW_TESTE_TLS="$TLS"
export PCFLOW_TESTE_CODIGO="$CODIGO"

gradle -p android :app:testDebugUnitTest --tests '*InteropTlsTest*' --no-daemon --rerun-tasks -i 2>&1 \
  | grep -viE 'JAVA_TOOL_OPTIONS' \
  | grep -E 'TLS negociado|PASSED|FAILED|tests? completed|> Task :app:testDebugUnitTest'
RESULTADO=${PIPESTATUS[0]}

echo
echo "--- registro do servidor ---"
grep -E '^(TLS_OK|TLS_FALHA|RECEBIDO|CONECTADO_ENVIADO|COMANDO|VERSAO_INCOMPATIVEL|SESSAO_ENCERRADA)' "$LOG" | head -30
exit "$RESULTADO"
