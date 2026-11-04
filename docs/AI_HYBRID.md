# IA híbrida do MEtanoia

## Arquitetura

- Android e PWA preservam seus bancos locais (Room e localStorage).
- A leitura contextual por regras continua disponível offline.
- Quando há internet, o usuário pode abrir a assistente integrada e enviar uma pergunta ao Worker do Cloudflare.
- O Worker chama o modelo @cf/meta/llama-3.3-70b-instruct-fp8-fast por meio do binding Workers AI.
- A conversa não é gravada pelo Worker. A conversa na interface é temporária; o contexto enviado é limitado aos registros recentes relevantes e à agenda.
- O usuário precisa marcar a autorização de envio antes de cada sessão de conversa.
- Android guarda a credencial cifrada com AES-GCM e Android Keystore. A PWA mantém a credencial apenas em sessionStorage.

## Proteção da API

O Worker exige o segredo METANOIA_AI_TOKEN no cabeçalho Authorization: Bearer. Não colocar esse segredo no código-fonte, no arquivo wrangler ou em variáveis públicas do frontend.

### Configuração única necessária no Cloudflare

1. Abra o painel Cloudflare e selecione Workers & Pages.
2. Abra o Worker metonoia.
3. Entre em Settings → Variables and Secrets.
4. Crie um secret chamado exatamente METANOIA_AI_TOKEN.
5. Use uma senha aleatória longa (mínimo recomendado: 32 caracteres), salve e não a publique.
6. Use essa mesma credencial no campo protegido da assistente no Android ou PWA.

O valor nunca deve ser commitado no GitHub. O deploy do Worker pode ocorrer sem o segredo, mas a API responderá 503 até que ele seja cadastrado.

## Modelo e custo

O modelo é hospedado pela Cloudflare Workers AI. O preço depende do volume de tokens de entrada e saída e pode mudar; consulte o catálogo e a página do modelo antes de uso recorrente. A página atual do modelo informa zero data retention e preço por milhão de tokens.

## Privacidade e limites

- Nenhum dado do Room ou do localStorage é sincronizado automaticamente para uma nuvem.
- O usuário escolhe o foco e autoriza explicitamente o envio do contexto selecionado.
- O contexto é reduzido aos registros recentes, confissões recentes e compromissos pertinentes; dados do cofre privado de Ingrid não são incluídos.
- O Worker não persiste prompts nem respostas em banco próprio.
- O token do Android é cifrado com chave mantida no Android Keystore; na PWA, fica somente na sessão atual do navegador.
- A IA depende de internet. Sem internet, a análise contextual local continua funcionando.
- A IA não é terapeuta, profissional de saúde, consultor financeiro nem substituto de acompanhamento pastoral.

## Endpoint

POST /api/ai/chat

Body: { dimension, context, question, history }

Response: { answer, model, mode: "cloud" }
