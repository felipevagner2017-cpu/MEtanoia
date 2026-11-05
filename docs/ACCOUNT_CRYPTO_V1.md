# Protocolo criptográfico de conta — versão 1

## Estado
Implementação dos primitivos interoperáveis Android (JCA) e PWA (Web Crypto API). Não está ativada sobre registros existentes e não autoriza migração ou exclusão. A criptografia local anterior vinculada ao Android Keystore não é interoperável com esta chave de conta.

## Formato
- Chave de dados aleatória por conta (DEK): 256 bits.
- Frase de criptografia independente da senha de login. A frase nunca deve ser enviada ao Worker.
- Derivação: PBKDF2-HMAC-SHA-256, salt aleatório de 128 bits, 600.000 iterações, saída de 256 bits.
- Envelope JSON: format metanoia-key-envelope, version 1, KDF, iterações, salt, nonce e ciphertext em Base64 padrão.
- Cifragem: AES-256-GCM, nonce aleatório de 96 bits e tag de 128 bits.
- AAD da chave: UTF-8 metanoia-account-key|1|<userId>.
- AAD do registro: UTF-8 metanoia-record|1|<userId>|<recordId>|<dimension>|<recordType>.
- Payload: v1.<Base64(nonce || ciphertext || tag)>.

## Segurança
A frase, KEK e DEK são manipuladas somente no cliente. O Worker deve receber apenas envelopes e payloads cifrados. O AAD vincula o envelope à conta e cada registro à conta, ID, dimensão e tipo; alterações desses campos invalidam a autenticação AES-GCM.

## Limitações ainda abertas
Esta etapa ainda não implementa recuperação por chave independente, rotação coordenada, re-encriptação após revogação de dispositivo, persistência D1 do envelope ou fluxo de cadastro/login que inicialize a chave. Sem a frase ou uma futura chave de recuperação, a DEK é irrecuperável. Não usar a senha de login como atalho: o endpoint de autenticação existente continua validando essa credencial.

A migração integral de Room exige SQLCipher, cópia verificável e journal de recuperação. Não trocar nem apagar a base original antes de validar contagens, integridade e relações em testes de interrupção.

## Compatibilidade
Android e Web precisam preservar parâmetros, Base64, AAD UTF-8, ordem nonce/ciphertext/tag e limites do KDF. Uma mudança incompatível exige nova versão.
