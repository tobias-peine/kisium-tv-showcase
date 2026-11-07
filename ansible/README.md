# Ansible-Verwaltung

Die Rolle `kisium_stack` synchronisiert das Projekt, templatet die `.env`,
startet den Docker-Stack, prüft den Healthcheck, verwaltet das TLS-Gateway,
UFW und Cronjobs.

## Voraussetzungen

- Ansible auf dem Control-Node
- SSH-Key-Login zum VPS
- `sudo` ohne Passwort oder `--ask-become-pass`
- Docker + Compose auf dem Zielsystem

## Inventar

```yaml
# ansible/inventory/hosts.yml
all:
  hosts:
    kisium_vps:
      ansible_host: <SERVER-IP>
      ansible_port: 22
      ansible_user: <USER>
      ansible_ssh_private_key_file: ~/.ssh/id_ed25519
```

## Secrets

`inventory/group_vars/all/vault.yml` wird **nicht** committed:

```bash
cd ansible
cp inventory/group_vars/all/vault.yml.example inventory/group_vars/all/vault.yml
ansible-vault encrypt inventory/group_vars/all/vault.yml
printf '%s\n' 'DEIN-VAULT-PASSWORT' > .vault-pass
chmod 600 .vault-pass
```

## Deployment

```bash
cd ansible
ansible-playbook playbooks/site.yml
```

Nur Syntax prüfen:

```bash
ansible-playbook playbooks/site.yml --syntax-check
```

## TLS aktivieren

`kisium_tls_enabled: false` ist der sichere Default. Für TLS zuerst ein
Let's-Encrypt-Zertifikat für die eigene Domain bereitstellen, dann in
`inventory/group_vars/all/vars.yml` auf `true` setzen und die Pfade
`kisium_letsencrypt_dir` / `kisium_tls_cert_name` anpassen.

## Was verwaltet wird

- Projektverzeichnis und `.env`
- Docker-Compose-Stack
- optional `kisium-tls` Nginx-Gateway auf Port 8443
- UFW: 8443 offen, 5000 nach außen geschlossen
- tägliche SQLite-/Katalog-Sicherung (14 Tage)
- tägliche Let's-Encrypt-Erneuerung + Nginx-Reload
