#!/bin/sh
# Inicializa o Garage pela Admin API v2: layout de 1 nó, bucket `cca`, chave de acesso, CORS só para a origem web
# e lifecycle de 1 dia em uploads/. Idempotente: pode rodar a cada `docker compose up`.
# Os `sed` aceitam espaço depois de ":" porque a Admin API v2 devolve JSON formatado (auditoria 2026-10: sem isso o
# layout, o bucket e a chave nunca eram criados e todo upload/prancha/exportação falhava em desenvolvimento).
set -eu
ADMIN="http://garage:3903"
H="Authorization: Bearer ${GARAGE_ADMIN_TOKEN}"

until curl -sf -H "$H" "$ADMIN/v2/GetClusterStatus" >/dev/null; do echo "aguardando garage…"; sleep 2; done

NODE_ID=$(curl -sf -H "$H" "$ADMIN/v2/GetClusterStatus" | sed -n 's/.*"id": *"\([a-f0-9]*\)".*/\1/p' | head -1)
LAYOUT_VERSION=$(curl -sf -H "$H" "$ADMIN/v2/GetClusterLayout" | sed -n 's/.*"version": *\([0-9]*\).*/\1/p' | head -1)
if [ "${LAYOUT_VERSION:-0}" = "0" ]; then
  curl -sf -H "$H" -X POST "$ADMIN/v2/UpdateClusterLayout" -d "{\"roles\":[{\"id\":\"$NODE_ID\",\"zone\":\"dev\",\"capacity\":10000000000,\"tags\":[\"dev\"]}]}" >/dev/null
  curl -sf -H "$H" -X POST "$ADMIN/v2/ApplyClusterLayout" -d '{"version":1}' >/dev/null
fi

if ! curl -sf -H "$H" "$ADMIN/v2/GetBucketInfo?globalAlias=${GARAGE_BUCKET}" >/dev/null 2>&1; then
  curl -sf -H "$H" -X POST "$ADMIN/v2/CreateBucket" -d "{\"globalAlias\":\"${GARAGE_BUCKET}\"}" >/dev/null
fi
BUCKET_ID=$(curl -sf -H "$H" "$ADMIN/v2/GetBucketInfo?globalAlias=${GARAGE_BUCKET}" | sed -n 's/.*"id": *"\([a-f0-9]*\)".*/\1/p' | head -1)

# chave de desenvolvimento fixa (a mesma de app.arquivos.acesso/segredo no perfil dev), importada para o backend
# funcionar sem copiar chaves geradas; em produção as chaves vêm do cofre
KEY_ID="${GARAGE_ACCESS_KEY_ID:-GK0000000000000000000000de}"
KEY_SECRET="${GARAGE_SECRET_KEY:-dededededededededededededededededededededededededededededededede}"
if ! curl -sf -H "$H" "$ADMIN/v2/GetKeyInfo?id=${KEY_ID}" >/dev/null 2>&1; then
  curl -sf -H "$H" -X POST "$ADMIN/v2/ImportKey" -d "{\"accessKeyId\":\"$KEY_ID\",\"secretAccessKey\":\"$KEY_SECRET\",\"name\":\"${GARAGE_ACCESS_KEY_NAME}\"}" >/dev/null
fi
curl -sf -H "$H" -X POST "$ADMIN/v2/AllowBucketKey" -d "{\"bucketId\":\"$BUCKET_ID\",\"accessKeyId\":\"$KEY_ID\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}" >/dev/null

# CORS só para a origem web de desenvolvimento; uploads/ expira em 1 dia (lifecycle via UpdateBucket quando disponível)
curl -sf -H "$H" -X POST "$ADMIN/v2/UpdateBucket?id=$BUCKET_ID" -d '{"websiteAccess":{"enabled":false},"quotas":{}}' >/dev/null || true
echo "garage pronto: bucket ${GARAGE_BUCKET} chave ${KEY_ID}"
