FROM mysql:8.0

# (Opcional) Garante o plugin de auth “clássico”
CMD ["--default-authentication-plugin=mysql_native_password"]

# Copia scripts de init para o entrypoint do MySQL
COPY init/*.sql /docker-entrypoint-initdb.d/
