# Connexió

Per connectar amb el servidor remot cal configurar l'arxiu **"config.env"** amb:

- El nom d'usuari 
- El *path* a l'arxiu *id_rsa*
- El port al que funciona el servidor

Per connectar amb el servidor remot es pot fer servir l'script:

```bash
# Des del terminal local
./proxmoxConnect.sh
# Obre una connexió "super" al terminal remot
```

El servidor remot ha de tenir els següents paquets instal·lats:

```bash
# Al servidor remot afegeix els paquets necessaris
sudo apt update
sudo apt install -y openjdk-21-jre procps grep gawk util-linux net-tools
exit
```

El servidor remot rep peticions pel port *80*, per seguretat és millor redirigir-les a un altre port (el del nostre servidor), per fer-ho:

```bash
# Al terminal local
./proxmoxRedirect80.sh
```

## Permisos de l'arxiu *id_rsa*

Cal que l'arxiu **"id_rsa"** només tingui permisos de lectura i escriptura per l'usuari.

Per aconseguir-ho:
```bash
chmod -x id_rsa
chmod go-rw id_rsa
ls -ltr id_rsa
# Ha de donar:
-rw-------@ 
```

## Depuració (debug)

Si necessites més informació sobre per què falla la connexió (p. ex. "Permission denied (publickey)"), executa l'script amb la variable d'entorn `DEBUG=1` per veure sortida detallada:

```bash
DEBUG=1 ./proxmoxConnect.sh
```

Aquest mode mostra la fingerprint de la clau que s'intenta usar i invoca `ssh` amb `-vvv` per facilitar el diagnòstic.

## Com enviar la teva clau pública a l'administrador

Si et trobes amb `Permission denied (publickey)`, cal que l'administrador del servidor afegeixi la teva clau pública a `/home/reverssd/.ssh/authorized_keys` del servidor remot. Per imprimir la teva clau pública local (llista per copiar), pots fer servir el script `print_pubkey.sh` que s'inclou al directori:

```bash
./print_pubkey.sh
# o equivalent
cat ~/.ssh/id_rsa.pub
```

Copieu la sortida (una sola línia) i envieu-la a l'administrador. Exemple de plantilla de missatge per enviar a l'administrador:

```
Hola,

Si us plau, afegiu aquesta clau pública al fitxer `/home/reverssd/.ssh/authorized_keys` del usuari `reverssd` en el servidor `ieticloudpro.ieti.cat`:

<PEGA_AQUI_EL_CONTINGUT_DE_ID_RSA.PUB>

Gràcies.
```

Instruccions que l'administrador pot executar al servidor (com a root o amb permisos):

```bash
mkdir -p /home/reverssd/.ssh
echo '<PEGA_AQUI_EL_CONTINGUT_DE_ID_RSA.PUB>' >> /home/reverssd/.ssh/authorized_keys
chown -R reverssd:reverssd /home/reverssd/.ssh
chmod 700 /home/reverssd/.ssh
chmod 600 /home/reverssd/.ssh/authorized_keys
```

Si preferiu generar una clau més moderna (ed25519) i enviar-la, executeu:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -C "reverssd@local"
cat ~/.ssh/id_ed25519.pub
```
