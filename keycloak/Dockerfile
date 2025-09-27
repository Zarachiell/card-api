FROM quay.io/keycloak/keycloak:24.0

# Copia o bootstrap já executável e com owner do usuário keycloak
COPY --chown=keycloak:keycloak --chmod=0755 init/bootstrap.sh /opt/keycloak-init/bootstrap.sh

ENTRYPOINT ["/opt/keycloak-init/bootstrap.sh"]
