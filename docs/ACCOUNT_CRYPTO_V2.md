# Protocolo de registros compartilhados — v2

## Estado desta etapa

Implementado o primitivo interoperável Android/PWA de criptografia de registros v2, baseado em padrões estabelecidos. Esta etapa ainda **não ativa a sincronização**: fila, persistência IndexedDB, identidade de dispositivo, API D1 e aplicação transacional de registros ainda precisam ser integradas e testadas ponta a ponta.

## Primitivos normativos

- Chave raiz: DEK aleatória de 256 bits já compartilhada pela conta através do envelope de conta v1.
- Derivação: HKDF-SHA-256 conforme RFC 5869, saída de 288 bits.
- Salt HKDF: UTF-8 literal `metonoia-sync-v2-hkdf-salt`.
- Info HKDF: codificação canônica length-prefixed dos campos:
  1. `metonoia-sync-record-v2`
  2. account ID
  3. source device ID
  4. purpose
- Material derivado: bytes 0..31 = chave AES-256; bytes 32..35 = prefixo de nonce de 32 bits.
- AEAD: AES-256-GCM, nonce de 96 bits, tag de 128 bits.
- Nonce: prefixo derivado de 32 bits seguido do contador persistente de 64 bits unsigned big-endian. Android restringe o contador ao intervalo positivo de SQLite `Long`.
- Payload: `v2.` + Base64 padrão de `counter[8] || ciphertext || tag[16]`.

## AAD autenticado

AES-GCM autentica a codificação canônica length-prefixed dos campos, na ordem exata:

`metonoia-sync-record-v2`, account ID, source device ID, purpose, record ID, dimension, record type, counter bytes.

Cada campo é precedido por seu comprimento em 32 bits big-endian. Strings usam UTF-8. O contador é exatamente 8 bytes big-endian. A codificação impede ambiguidades de concatenação.

## Consequências de segurança

- DEKs diferentes, contas diferentes, dispositivos de origem diferentes ou finalidades diferentes derivam chaves distintas.
- Um dispositivo autorizado que possui a DEK pode derivar a chave do dispositivo de origem e autenticar/decriptar o registro.
- Alterar conta, dispositivo de origem, finalidade, ID, dimensão, tipo ou contador invalida a autenticação.
- Contadores reservados nunca podem ser reutilizados, mesmo quando a operação de criptografia falha depois da reserva. Lacunas são permitidas.
- O ID de origem precisa ser emitido e validado pelo servidor, não aceito livremente do payload do cliente.
- O servidor deve preservar `source_device_id`, `purpose`, versão e ciphertext sem descriptografar conteúdo.

## Compatibilidade

- `v1.` continua sendo lido pelo caminho legado local; não converter automaticamente nem descartar registros v1.
- `v2.` é reservado para registros criptografados com a DEK compartilhada.
- Nunca usar a chave Android Keystore de uma instalação para produzir novos registros compartilhados.
- Não declarar sincronização concluída até que push, pull, aplicação Room transacional, IndexedDB, conflitos, cursores, revogação e testes Android/PWA estejam integrados.

## Vetor determinístico

DEK = bytes 00..1f; account = `interop-user-v2`; source device = `device-android-01`; purpose = `record`; record ID = `record-v2-01`; dimension = `DEVOTION`; type = `journal`; counter = 42; plaintext = `Conteúdo compartilhado Android/PWA — vetor v2`.

Payload esperado:

`v2.AAAAAAAAACqcwwxL0VXPNJ4sfTkb+YRGhVourP2v6px5/LWgS4rMDmXu+zpsLHeQe/jyU9fnjsmwA3uglaviKRnXZW7SrhQe`

Este vetor foi calculado com WebCrypto e deve ser mantido fixo nos testes Kotlin e JavaScript.
