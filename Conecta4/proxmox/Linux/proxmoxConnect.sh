#!/bin/bash

# Connects to the remote server using SSH

source ./config.env

# Arguments: [user] [rsa_path] [port]
# Default order: arg -> DEFAULT_* from config.env -> environment
USER=${1:-${DEFAULT_USER:-$USER}}
RSA_PATH=${2:-"${DEFAULT_RSA_PATH:-$HOME/.ssh/id_rsa}"}
RSA_PATH="${RSA_PATH%$'\r'}"
PORT=${3:-${DEFAULT_SERVER_PORT:-20127}}

# SSH options: keep existing rsa algorithm options and force using the
# provided identity only (helps avoid trying other keys from agent)
SSH_OPTS='-oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa -o IdentitiesOnly=yes'

echo "User: $USER"
echo "Ruta RSA: $RSA_PATH"
echo "Port: $PORT"

if [[ ! -f "${RSA_PATH}" ]]; then
  echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
  exit 1
fi

# If DEBUG=1 in environment, add verbose flags for debugging
SSH_DEBUG_OPTS=""
if [[ "${DEBUG}" == "1" ]]; then
  SSH_DEBUG_OPTS='-vvv'
  echo "DEBUG: activat (ssh -vvv)"
fi

# Establish SSH connection
ssh -i "${RSA_PATH}" -p "${PORT}" $SSH_DEBUG_OPTS $SSH_OPTS "$USER@ieticloudpro.ieti.cat"