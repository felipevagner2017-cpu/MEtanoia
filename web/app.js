(()=> {
const $=id=>document.getElementById(id),K="metonoia-pwa-v1",areas=["Espiritual","Matrimonial","Profissional","Cotidiano"],days=["Domingo","Segunda-feira","Terça-feira","Quarta-feira","Quinta-feira","Sexta-feira","Sábado"];
let syncEngine=null,db;try{db=JSON.parse(localStorage.getItem(K))||{items:[],journal:[],reviews:[],words:[]}}catch{db={items:[],journal:[],reviews:[],words:[]}}
db.items=db.items||[];db.journal=db.journal||[];db.reviews=db.reviews||[];db.words=db.words||[];
const uid=()=>crypto.randomUUID?.()||String(Date.now()+Math.random()),esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const key=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,"0"),String(d.getDate()).padStart(2,"0")].join("-"),parse=k=>new Date(k+"T00:00:00"),today=()=>key(new Date()),fmt=k=>parse(k).toLocaleDateString("pt-BR",{day:"2-digit",month:"long",year:"numeric"});
const fixed=x=>x.schedule==="daily"||x.schedule==="weekly"||x.fixedWeekly===true,area=x=>areas.includes(x)?x:(x==="Individual"||x==="Agenda e rotina"?"Cotidiano":"Cotidiano");
db.items.forEach(x=>{x.dimension=area(x.dimension);if(x.fixedWeekly===true&&!x.schedule)x.schedule="weekly";if(x.repeat==="weekly"&&!x.schedule)x.schedule="weekly";if(x.schedule==="weekly"&&!Array.isArray(x.weekdays))x.weekdays=[1,2,3,4,5];});
db.journal.forEach(x=>x.dimension=area(x.dimension));localStorage.setItem(K,JSON.stringify(db));
let selected= new Date();selected.setHours(0,0,0,0);let agendaDate=key(selected),calendarMonth=new Date(selected.getFullYear(),selected.getMonth(),1),agendaViewMode="month",observedToday=today(),promptInstall=null;
const save=()=>{localStorage.setItem(K,JSON.stringify(db));syncEngine?.schedule()};
const occurrence=(x,k)=>x.completions?.[k]||{done:!!x.done&&x.date===k,note:""};
function occursOn(x,d){const k=key(d);if(!fixed(x))return x.date===k;if(k<(x.date||"0000-01-01")||(x.endDate&&k>x.endDate))return false;if(x.schedule==="daily")return true;return (Array.isArray(x.weekdays)?x.weekdays:[]).includes(d.getDay())}function forDate(d){return db.items.filter(x=>occursOn(x,d)).sort((a,b)=>(a.time||"").localeCompare(b.time||""))}
const categoryColor=x=>({"Espiritual":"#7986CB","Matrimonial":"#E67C73","Profissional":"#039BE5","Cotidiano":"#33B679"}[area(x.dimension)]||"#9E9E9E");
function suggestEventEmoji(title,kind,dimension){
 const t=(String(title||"")+" "+String(kind||"")+" "+String(dimension||"")).toLowerCase();
 if(/igreja|culto|pastor|minist[eé]rio|congrega|celebra/.test(t))return "🏠";
 if(/pet|cachorr|c[aã]o|gato|animal/.test(t))return "🐶";
 if(/profission|trabalho|empresa|cliente|venda|reuni[aã]o|entrevista|neg[oó]cio/.test(t))return "👔";
 if(/consulta|m[eé]dic|dent|exame|sa[uú]de|terapia/.test(t))return "🏥";
 if(/viagem|desloc|carro|aeroporto|estrada/.test(t))return "🚗";
 if(/casal|esposa|marido|matrim[oô]nio/.test(t))return "💍";
 if(/ora[cç][aã]o|b[ií]blia|leitura|devocional|estudo b[ií]blico/.test(t)||dimension==="Espiritual")return "📖";
 if(/anivers[aá]rio|festa|amigo|fam[ií]lia|almo[cç]o|jantar/.test(t))return "🎉";
 if(/limpeza|casa|organiza[cç][aã]o|lavar|mercado|compras/.test(t))return "🏡";
 return "📅";
}
const eventEmojiOptions=["📅","🏠","🐶","🐱","👔","💼","📖","🙏","💍","❤️","🏥","💊","🚗","✈️","🎉","👨‍👩‍👧‍👦","🏡","🛒","⚽","🏋️"];
const guidance={
"Espiritual":{ref:"João 15:1–8; Salmo 139:23–24",principle:"Jesus ensina que o fruto nasce da permanência nele. O exame do coração deve conduzir à dependência de Deus e à obediência, não à aparência religiosa.",reflect:"O que seu relato revela sobre sua comunhão com Deus, suas motivações e sua resposta à Palavra?",action:"Separe um tempo real para ler o texto no contexto, orar com sinceridade e praticar uma instrução específica que ele apresenta."},
"Matrimonial":{ref:"Efésios 4:1–3, 25–32; Colossenses 3:12–19",principle:"A nova vida em Cristo aparece na maneira de falar, ouvir, perdoar e tratar o cônjuge. Amor bíblico envolve humildade, verdade, paciência e ações concretas.",reflect:"No episódio relatado, o que precisava ser ouvido, dito com verdade, reparado ou perdoado?",action:"Escolha uma atitude observável: ouvir sem interromper, pedir perdão sem justificativas, falar a verdade com mansidão ou servir em algo concreto."},
"Profissional":{ref:"Colossenses 3:22–24; Provérbios 11:1; Efésios 6:5–8",principle:"O trabalho deve ser conduzido com integridade, justiça e diligência diante do Senhor. Resultado não justifica desonestidade nem negligência.",reflect:"Seu relato aponta para uma decisão de integridade, responsabilidade, excelência ou justiça que precisa ser tomada?",action:"Defina o próximo passo correto, comunique com clareza e cumpra o que foi assumido sem atalhos desonestos."},
"Cotidiano":{ref:"Efésios 5:15–17; Provérbios 4:23–27; Tiago 1:19–20",principle:"A sabedoria bíblica alcança o uso do tempo, as reações, as palavras e as escolhas comuns. Discernir o caminho inclui desacelerar e responder com domínio próprio.",reflect:"Que padrão de pensamento, reação, uso do tempo ou decisão cotidiana aparece no que você escreveu?",action:"Identifique uma mudança pequena e verificável para hoje: organizar uma prioridade, interromper uma reação impulsiva ou resolver uma pendência com sabedoria."}
};
const patterns=[
{words:["promocao","promovido","promovida","aumento","reconhecimento","crescer profissional","subir de cargo","expectativa de promocao"],areas:["Profissional"],name:"Expectativa de crescimento profissional",ref:"Salmo 37:3–7; 1 Pedro 5:6–7; Filipenses 4:6–9",advice:"Buscar crescimento profissional não é errado. A Escritura, porém, chama você a confiar no Senhor, praticar o bem e entregar a Ele o caminho, sem permitir que a expectativa pelo resultado governe o coração. Humildade e confiança não significam passividade: continue trabalhando com excelência e integridade, enquanto reconhece que o tempo e a decisão final não estão totalmente sob seu controle.",step:"Prepare-se para a oportunidade, peça uma conversa clara sobre critérios de promoção, cumpra suas responsabilidades com excelência e entregue a ansiedade a Deus em oração."},
{words:["dificuldade","dificil","desentendimento","conversa dificil","frieza","desacordo","briga","dificuldade no matrimonio","dificuldade no casamento","problema no casamento","nao me entende","nao estamos bem","distante de mim","conflito conjugal"],areas:["Matrimonial"],name:"Dificuldade e diálogo no matrimônio",ref:"Tiago 1:19–20; Efésios 4:25–32; Colossenses 3:12–14",advice:"A Palavra orienta a ouvir com prontidão, falar com verdade e abandonar respostas dominadas pela ira. Isso não apaga a dor nem coloca toda a responsabilidade em uma só pessoa; chama cada um a examinar sua própria postura diante de Deus.",step:"Descreva com clareza o que aconteceu, escute a perspectiva do outro sem interromper e proponha uma conversa em momento adequado, com mansidão e verdade."},

{words:["ansied","medo","preocup","insegur"],name:"Ansiedade ou medo",ref:"Mateus 6:25–34; Filipenses 4:6–9",advice:"Jesus não banaliza as necessidades; ensina a não deixar a ansiedade governar o coração. Paulo orienta apresentar os pedidos a Deus e ocupar a mente com o que é verdadeiro e justo.",step:"Escreva a preocupação, separe o que está sob sua responsabilidade do que não controla e dê hoje um passo fiel, em oração."},
{words:["ira","raiva","briga","discuss","conflit","grit"],name:"Conflito ou ira",ref:"Tiago 1:19–20; Efésios 4:26–32",advice:"A Escritura chama à prontidão para ouvir, lentidão para falar e para irar-se. A ira humana não produz a justiça de Deus; conflitos exigem verdade, domínio próprio e reparação.",step:"Antes de responder, escute; depois converse com verdade e mansidão. Se errou, reconheça especificamente e procure reparar."},
{words:["cans","exaust","sobrecar","desgast"],name:"Cansaço e sobrecarga",ref:"Marcos 6:30–32; Mateus 11:28–30",advice:"Jesus acolhe os discípulos para descansar e chama os cansados a encontrarem nele descanso. Descanso e limites não são fracasso.",step:"Reavalie o que realmente precisa ser feito hoje, estabeleça um limite possível e reserve um tempo de descanso."},
{words:["ment","mentir","verdade","integridade","desonest"],name:"Verdade e integridade",ref:"Efésios 4:25; Provérbios 10:9",advice:"A nova vida exige abandonar a falsidade e falar a verdade. Integridade é caminhar sem duplicidade, mesmo quando isso tem custo.",step:"Nomeie a verdade que precisa ser dita e tome a providência correta, sem encobrir o erro."},
{words:["perd","mágo","ressent","ofend"],name:"Perdão e mágoa",ref:"Efésios 4:31–32; Colossenses 3:13",advice:"O perdão cristão nasce da graça recebida em Cristo. Perdoar não significa negar o dano nem dispensar limites e justiça.",step:"Identifique a ofensa com honestidade e escolha um passo seguro de perdão, conversa ou estabelecimento de limites."},
{words:["procrast","atras","desorgan","tempo","priorid"],name:"Tempo e prioridades",ref:"Efésios 5:15–17; Provérbios 6:6–11",advice:"A sabedoria bíblica chama a viver com atenção e discernimento, aproveitando bem o tempo. Planejamento deve servir à fidelidade, não à ansiedade.",step:"Escolha a prioridade mais importante, defina quando será feita e reduza uma distração concreta."},
{words:["oração","orar","bíblia","palavra","devoc","jejum","igreja"],name:"Vida espiritual",ref:"Salmo 1:1–3; João 15:4–5; Tiago 1:22",advice:"A vida com Deus envolve permanecer na Palavra e responder a ela em obediência. Prática espiritual não é desempenho para impressionar, mas relacionamento e fidelidade.",step:"Volte ao texto bíblico, observe o que ele realmente ensina e transforme uma verdade em obediência prática."}
];
let activeWordArea=null,selectedWordAreas=[];const wordOrder=["Espiritual","Matrimonial","Profissional","Cotidiano"];
function normalizeText(value){return String(value||"").toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g,"")}
function bestPattern(records,areaName){
 const ranked=patterns.map((p,index)=>({p,index,score:records.reduce((total,record,position)=>{const text=normalizeText((record.title||"")+" "+(record.body||"")),weight=Math.max(1,8-position);return total+p.words.reduce((n,w)=>n+(text.includes(normalizeText(w))?weight:0),0)},0)}))
  .filter(x=>x.score>0&&(!x.p.areas||x.p.areas.includes(areaName)))
  .sort((x,y)=>y.score-x.score||x.index-y.index);
 return ranked[0]?.p||null;
}
function wordContent(a){
 const g=guidance[a],records=db.journal.filter(x=>area(x.dimension)===a).sort((x,y)=>y.at-x.at),recent=records.slice(0,8),match=bestPattern(recent,a);
 const topic=recent[0]?(recent[0].title||recent[0].body.slice(0,110)):"Nenhum relato registrado nesta área";
 const body=match?'<div class="eyebrow">Tema identificado nos seus registros</div><h3>'+esc(match.name)+'</h3><p>'+esc(match.advice)+'</p><div class="goldtext">'+esc(match.ref)+'</div><p><b>Próximo passo:</b> '+esc(match.step)+'</p>':'<div class="eyebrow">Ensinamento bíblico</div><div class="goldtext">'+esc(g.ref)+'</div><p>'+esc(g.principle)+'</p><p><b>Examine:</b> '+esc(g.reflect)+'</p><p><b>Prática:</b> '+esc(g.action)+'</p>';
 return '<article class="reading-page"><div class="eyebrow">Item 0'+(wordOrder.indexOf(a)+1)+' · '+(a==="Espiritual"?"Devoção a Deus":a)+'</div><h2>'+(a==="Espiritual"?"Devoção a Deus":a)+'</h2><div class="reading-topic"><span class="muted">Ponto de partida dos seus registros</span><p>'+esc(topic)+'</p></div>'+body+'<p class="reading-note">Leia a passagem no contexto. A orientação considera o que você registrou e não substitui discernimento, cuidado profissional ou acompanhamento pastoral.</p></article>';
}
let activeWordIndex=null;
const wordChallenges={
 "Espiritual":"Separe 15 minutos sem distrações. Leia a passagem indicada, anote uma verdade sobre Deus e pratique hoje uma orientação que ela apresenta.",
 "Matrimonial":"Tome uma iniciativa concreta de cuidado: escute sem interromper, reconheça algo que precisa reparar ou demonstre gratidão de forma específica.",
 "Profissional":"Escolha uma responsabilidade importante e avance nela com excelência e integridade. Defina o próximo passo e conclua-o sem atalhos.",
 "Cotidiano":"Escolha um cuidado possível para hoje: movimento, alimentação, descanso ou organização. Registre o que realmente fez, sem se cobrar pelo que não conseguiu."
};
const dailyGuidance={"Espiritual":[["João 15:1–8","Jesus apresenta a permanência nele como a fonte do fruto. A vida espiritual não é sustentada por aparência, mas por comunhão contínua com Cristo.","O que tem ocupado o lugar da comunhão com Cristo na sua rotina?","Separe um tempo sem distrações para ler o texto, orar e praticar uma orientação de Jesus."],["Miquéias 6:6–8","Deus chama seu povo a praticar a justiça, amar a misericórdia e caminhar humildemente com Ele. Devoção verdadeira alcança o modo de viver.","Onde você pode agir com mais justiça e misericórdia hoje?","Pratique uma atitude concreta de justiça ou misericórdia sem buscar reconhecimento."],["Salmo 139:23–24","O salmista convida Deus a examinar o coração e conduzir pelo caminho eterno. O exame bíblico visa correção e retorno, não condenação paralisante.","Que caminho, motivação ou reação precisa ser colocado diante de Deus?","Ore com sinceridade, reconheça um ajuste necessário e dê um passo concreto para mudar."],["Mateus 6:5–13","Jesus ensina uma oração centrada no Pai, no Reino, no pão diário e no perdão. Orar não é representar espiritualidade; é depender de Deus.","Sua oração tem sido comunhão ou apenas uma lista de urgências?","Ore com atenção ao Pai, apresente uma necessidade e interceda por alguém pelo nome."],["Hebreus 12:1–3","A perseverança cristã exige deixar pesos e manter os olhos em Jesus. A disciplina espiritual nasce de uma caminhada focada nele.","Que distração ou peso está enfraquecendo sua constância?","Retire uma distração hoje e proteja um horário para sua comunhão com Deus."],["Efésios 4:1–3","A vocação cristã se expressa em humildade, mansidão, paciência e cuidado com a unidade. Maturidade espiritual aparece também nos relacionamentos.","Em qual relacionamento sua fé precisa aparecer com mais mansidão?","Tome a iniciativa de ouvir, reparar uma atitude ou servir alguém com humildade."],["Romanos 12:1–2","A transformação envolve mente renovada e discernimento da vontade de Deus. A fé alcança escolhas concretas, não apenas convicções declaradas.","Que padrão de pensamento precisa ser confrontado pela Palavra?","Identifique uma reação automática e escolha uma resposta alinhada à verdade bíblica."]],"Matrimonial":[["Efésios 4:25–32","Verdade, bondade, perdão e amor sustentam relações maduras. Cada pessoa é chamada a assumir responsabilidade por suas palavras e atitudes.","Que atitude sua pode construir confiança e segurança hoje?","Escute sem interromper, reconheça algo bom ou repare uma atitude sem se justificar."],["Tiago 1:19–20","Ouvir com prontidão e falar com calma não significa esconder a verdade. Significa não permitir que a ira governe a conversa.","O que você precisa compreender antes de tentar ser compreendido?","Faça uma pergunta sincera e escute a resposta sem preparar uma defesa."],["1 Coríntios 13:4–7","O amor é paciente, bondoso e não busca apenas o próprio interesse. É demonstrado por atitudes, sem justificar violência ou desrespeito.","Qual gesto concreto expressaria amor paciente e respeitoso?","Ofereça um gesto de cuidado que respeite a vontade e o momento da outra pessoa."],["Eclesiastes 4:9–12","A parceria permite compartilhar cargas e oferecer apoio. Caminhar junto não apaga limites nem responsabilidades individuais.","Que peso poderia ser compartilhado em vez de ficar silenciosamente sobre o outro?","Pergunte o que poderia aliviar o dia da pessoa amada e ajude se ela desejar."],["Provérbios 18:13; Filipenses 2:3–4","Responder antes de ouvir produz ruído. A humildade considera também a perspectiva e as necessidades do outro.","Você tem ouvido para compreender ou apenas para responder?","Na próxima conversa, repita o que entendeu antes de apresentar sua visão."],["Romanos 12:9–18","A paz requer sinceridade, honra e esforço responsável. Nem toda reconciliação depende de uma pessoa só, mas cada um pode agir com integridade.","Qual passo de paz está ao seu alcance sem negar limites necessários?","Dê um passo respeitoso em direção à paz ou prepare uma conversa segura e honesta."],["1 João 3:18; Cânticos 8:6–7","O amor não se limita a declarações; ele se expressa em compromisso e verdade nas ações cotidianas.","Que atitude de constância pode comunicar compromisso hoje?","Expresse gratidão específica e transforme-a em uma ação simples de cuidado."]],"Profissional":[["Colossenses 3:23–24; Provérbios 11:1","Integridade é fazer o que é correto mesmo quando ninguém observa. Excelência não justifica atalhos desonestos.","Há alguma decisão em que resultado e integridade parecem estar em tensão?","Avance na tarefa mais importante com excelência, transparência e responsabilidade."],["Provérbios 21:5; Lucas 14:28–30","Planejamento cuidadoso considera recursos, etapas e consequências. Diligência evita tanto a impulsividade quanto a paralisia.","Qual objetivo precisa ser dividido em passos menores e realistas?","Defina uma prioridade, o primeiro passo e um critério claro de conclusão."],["Provérbios 16:3; Tiago 4:13–15","Planejar é legítimo, mas o futuro não está sob controle absoluto. Entregar os planos a Deus combina humildade e ação responsável.","Você está confundindo planejamento com a necessidade de controlar todos os resultados?","Organize o próximo passo e entregue a Deus aquilo que não pode controlar."],["Efésios 6:5–8; Provérbios 22:29","Competência e dedicação têm valor quando caminham com justiça e honestidade. Trabalho é serviço, não a medida total da identidade.","Como desenvolver sua competência sem sacrificar caráter ou saúde?","Dedique 20 minutos a uma habilidade e aplique o aprendizado em uma tarefa concreta."],["Provérbios 15:22; 19:20","Conselho sábio amplia a visão e ajuda a corrigir decisões. Pedir orientação pode evitar erros previsíveis.","Que decisão merece ser examinada por alguém experiente e confiável?","Peça orientação específica e transforme o conselho em um próximo passo avaliável."],["Mateus 6:31–34; Filipenses 4:11–13","A ansiedade pelo amanhã pode consumir a responsabilidade de hoje. A confiança em Deus não elimina o trabalho fiel no presente.","Qual preocupação futura está roubando sua atenção de hoje?","Separe o que depende de você e conclua uma ação possível neste dia."],["Provérbios 10:4; 2 Tessalonicenses 3:10–13","Diligência envolve constância e responsabilidade, não ativismo sem descanso. O trabalho deve ser útil e sustentável.","Você está adiando uma responsabilidade ou tentando carregar mais do que é possível?","Conclua uma pendência importante e defina um limite saudável para encerrar o expediente."]],"Cotidiano":[["Efésios 5:15–17; Salmo 90:12","A sabedoria considera como o tempo é usado. Viver com intenção é reconhecer prioridades, sem preencher cada minuto.","O que tem recebido seu tempo sem realmente merecer sua atenção?","Escolha uma prioridade realista e proteja um período para ela, incluindo descanso."],["1 Coríntios 6:19–20; 1 Timóteo 4:8","O cuidado do corpo tem valor e deve ser tratado com equilíbrio. Disciplina física não define o valor de uma pessoa.","Que cuidado possível com seu corpo você vem adiando?","Faça uma escolha possível de cuidado: movimento, alimentação, hidratação ou descanso."],["Provérbios 4:23–27; Filipenses 4:8","Guardar o coração inclui prestar atenção ao que alimenta pensamentos e orienta decisões. A mente precisa de direção, não de sobrecarga.","Que conteúdo, hábito ou pensamento está influenciando suas reações?","Reduza uma fonte de distração e escolha conscientemente algo verdadeiro e edificante."],["Marcos 6:31; Salmo 127:1–2","O descanso faz parte da vida humana e não é sinal de fracasso. Jesus reconhece a necessidade de parar.","Você tem respeitado seus limites ou tratado o descanso como algo que precisa merecer?","Reserve um intervalo real de descanso e desligue-se de uma tarefa que pode esperar."],["Tiago 1:19–20; Provérbios 14:29","A rotina revela maturidade nas reações. A lentidão para irar-se cria espaço para responder com domínio próprio.","Em qual situação você precisa trocar a reação automática por uma resposta consciente?","Quando surgir irritação, faça uma pausa e escolha uma resposta mais sábia."],["Provérbios 24:3–4; 1 Coríntios 14:40","Organização pode servir à paz e ao cuidado. A ordem não precisa ser perfeccionista; deve tornar a vida funcional.","Que pequeno espaço ou processo organizado reduziria o peso do seu dia?","Organize por 15 minutos um espaço ou uma pendência que facilitará sua semana."],["Gálatas 6:9–10; Eclesiastes 3:1","Há tempo para diferentes responsabilidades e o bem é construído com perseverança. Constância não exige fazer tudo ao mesmo tempo.","Que responsabilidade precisa de perseverança e qual pode aguardar sua estação?","Faça um pequeno bem que está ao seu alcance e reconheça o que ficará para outro dia."]]};
const dailyIndex=()=>{const d=parse(today()),start=new Date(d.getFullYear(),0,1);return Math.floor((d-start)/86400000)%7};
function renderDailyJourney(){
 const root=$("dailyJourney");if(!root)return;
 const daily=dailyGuidance.Espiritual[dailyIndex()];
 const records=db.journal.slice().sort((a,b)=>b.at-a.at).slice(0,8);
 const match=bestPattern(records,"Espiritual")||bestPattern(records,"Matrimonial")||bestPattern(records,"Profissional")||bestPattern(records,"Cotidiano");
 root.innerHTML='<article class="panel daily-journey-card"><div class="eyebrow">MEtonoia · sua caminhada com propósito</div><h2>Jornada diária</h2><p class="muted">Uma palavra para orientar sua mente e suas escolhas de hoje.</p><div class="daily-word-source"><span class="word-kicker">Palavra de hoje</span><div class="goldtext">'+esc(daily[0])+'</div><p>'+esc(daily[1])+'</p><p><b>Para refletir:</b> '+esc(daily[2])+'</p></div>'+(match?'<div class="personal-direction"><div class="eyebrow">Considerando seus registros recentes</div><h3>'+esc(match.name)+'</h3><p>'+esc(match.advice)+'</p><div class="goldtext">'+esc(match.ref)+'</div></div>':'')+'<div class="word-challenge"><div class="challenge-mark">PRÁTICA DE HOJE</div><p>'+esc(daily[3])+'</p></div><p class="reading-note">Leia a passagem bíblica no contexto e transforme o entendimento em uma atitude concreta.</p></article>';
}
function renderWord(){
 const root=$("wordAreas");
 const descriptions={"Espiritual":"Comunhão, devoção, Palavra e obediência","Matrimonial":"Aliança, diálogo e cuidado mútuo","Profissional":"Propósito, decisões e desenvolvimento","Cotidiano":"Hábitos, saúde, emoções e organização"};
 const labels={"Espiritual":"Devocional","Matrimonial":"Matrimônio","Profissional":"Profissional","Cotidiano":"Cotidiano"};
 const icons={"Espiritual":"✧","Matrimonial":"♡","Profissional":"◈","Cotidiano":"⌂"};
 const pages=wordOrder.map((a,i)=>{
  const daily=dailyGuidance[a][dailyIndex()],records=db.journal.filter(x=>area(x.dimension)===a).sort((x,y)=>y.at-x.at),match=bestPattern(records.slice(0,8),a);
  const content='<div class="daily-word-source"><span class="word-kicker">Palavra de hoje</span><div class="goldtext">'+esc(daily[0])+'</div><p>'+esc(daily[1])+'</p><p class="word-reflection"><b>Para refletir:</b> '+esc(daily[2])+'</p></div>'+(match?'<div class="daily-word-source personal-direction"><span class="word-kicker">Uma direção a partir dos seus registros</span><h3>'+esc(match.name)+'</h3><p>'+esc(match.advice)+'</p><div class="goldtext">'+esc(match.ref)+'</div></div>':'');
  return '<article class="word-slide" data-word-slide="'+a+'" aria-label="'+labels[a]+'"><div class="word-slide-head">'+(a==="Matrimonial"?'<img class="word-dimension-avatar" src="/icons/ingrid_avatar.webp" alt="Ingrid">':'<span class="word-icon">'+icons[a]+'</span>')+'<div><div class="eyebrow">Jornada diária · 0'+(i+1)+' de 04</div><h2>'+labels[a]+'</h2><p>'+descriptions[a]+'</p></div></div>'+content+'<div class="word-challenge"><div class="challenge-mark">DESAFIO DE HOJE</div><p>'+daily[3]+'</p></div><div class="word-slide-actions"><button class="btn gold" type="button" data-open-section="'+(a==="Espiritual"?"pastoralStudy":a==="Matrimonial"?"ingrid":a==="Profissional"?"professional":a==="Cotidiano"?"cotidiano":"diary")+'" data-dimension="'+a+'">'+(a==="Espiritual"?"Abrir estudo pastoral":"Abrir "+labels[a])+'</button></div><div class="word-slide-foot"><span>MEtanoia · Palavra e prática</span><span>'+(i+1)+' / 4</span></div></article>';
 }).join("");
 root.innerHTML='<div class="word-welcome"><div class="word-welcome-copy"><div class="eyebrow">Sua jornada diária</div><h2>Jornada diária</h2><p>Deslize para a direita ou esquerda entre Devocional, Matrimônio, Profissional e Cotidiano. Cada dimensão reúne uma mensagem e um desafio prático para hoje.</p></div><div class="word-progress"><span>04</span><small>ÁREAS DE<br>TRANSFORMAÇÃO</small></div></div><div class="word-topic-rail" id="wordTopicRail" aria-label="Escolher área">'+wordOrder.map((a,i)=>'<button class="word-topic '+(i===activeWordIndex?"active":"")+'" type="button" data-word-go="'+i+'"><span>'+icons[a]+'</span><b>'+labels[a]+'</b><small>0'+(i+1)+'</small></button>').join("")+'</div><div class="word-swipe-hint"><span>DESLIZE PARA EXPLORAR</span><span class="word-hint-arrows">←　→</span></div><div class="word-carousel" id="wordCarousel">'+pages+'</div><div class="word-pagination" id="wordPagination">'+wordOrder.map((a,i)=>'<button type="button" data-word-go="'+i+'" class="'+(i===activeWordIndex?"active":"")+'" aria-label="Abrir '+labels[a]+'"></button>').join("")+'</div><p class="word-disclaimer">A reflexão considera princípios bíblicos e os registros que você escolheu compartilhar. Leia sempre a passagem no contexto.</p>';
 const carousel=$("wordCarousel"),rail=$("wordTopicRail");
 const go=(index)=>{activeWordIndex=Math.max(0,Math.min(wordOrder.length-1,index));const slide=carousel.querySelectorAll(".word-slide")[activeWordIndex];slide?.scrollIntoView({behavior:"smooth",block:"nearest",inline:"start"});syncWordNav();};
 const syncWordNav=()=>{const slides=[...carousel.querySelectorAll(".word-slide")],rect=carousel.getBoundingClientRect(),center=rect.left+rect.width/2;let best=0,dist=Infinity;slides.forEach((el,i)=>{const r=el.getBoundingClientRect(),d=Math.abs(r.left+r.width/2-center);if(d<dist){dist=d;best=i}});activeWordIndex=best;root.querySelectorAll("[data-word-go]").forEach(el=>el.classList.toggle("active",Number(el.dataset.wordGo)===best));const active=rail.querySelector(".word-topic.active");active?.scrollIntoView({behavior:"smooth",block:"nearest",inline:"center"});};
 root.querySelectorAll("[data-word-go]").forEach(el=>el.addEventListener("click",()=>go(Number(el.dataset.wordGo))));
 let scrollTimer;carousel.addEventListener("scroll",()=>{clearTimeout(scrollTimer);scrollTimer=setTimeout(syncWordNav,70)},{passive:true});
 $("todayDate").textContent=new Date().toLocaleDateString("pt-BR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
}
function card(x,k){const o=occurrence(x,k);return '<div class="item '+(o.done?"done":"")+'"><div class="grow"><button class="item-action" data-answer="'+esc(x.id)+'" data-date="'+k+'"><strong>'+esc(x.title)+'</strong><div class="muted">'+esc((x.time||"Sem horário")+(x.endTime?" – "+x.endTime:""))+' · '+esc(x.dimension||"Cotidiano")+' · '+esc(x.kind||"Compromisso")+'</div><div class="statusline">'+(o.done?"✓ Concluído — confirmado por você":o.note?"Não concluído — "+esc(o.note):"Toque para confirmar se realizou")+'</div></button></div><button class="link" data-edit="'+esc(x.id)+'">Editar</button><button class="link" data-del="'+esc(x.id)+'">Excluir</button></div>'}
function renderCalendar(){
 const selectedDate=parse(agendaDate),weekdaysMonth=["Dom","Seg","Ter","Qua","Qui","Sex","Sáb"],weekdaysWeek=["Seg","Ter","Qua","Qui","Sex","Sáb","Dom"];
 let dates=[],title="",weekView=agendaViewMode==="week";
 if(agendaViewMode==="day"){
  dates=[selectedDate];title=selectedDate.toLocaleDateString("pt-BR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
  $("calendarGrid").style.display="none";
 }else if(weekView){
  const start=new Date(selectedDate);start.setDate(start.getDate()-((start.getDay()+6)%7));
  dates=Array.from({length:7},(_,i)=>new Date(start.getFullYear(),start.getMonth(),start.getDate()+i));
  title="Semana · "+start.toLocaleDateString("pt-BR",{day:"2-digit",month:"short"})+" – "+dates[6].toLocaleDateString("pt-BR",{day:"2-digit",month:"short",year:"numeric"});
  $("calendarGrid").style.display="grid";
 }else{
  const first=new Date(calendarMonth.getFullYear(),calendarMonth.getMonth(),1),offset=first.getDay(),daysInMonth=new Date(first.getFullYear(),first.getMonth()+1,0).getDate(),cells=Math.ceil((offset+daysInMonth)/7)*7;
  dates=Array.from({length:cells},(_,i)=>new Date(first.getFullYear(),first.getMonth(),i-offset+1));
  title=first.toLocaleDateString("pt-BR",{month:"long",year:"numeric"});
  $("calendarGrid").style.display="grid";
 }
 const weekdays=weekView?weekdaysWeek:weekdaysMonth;
 let out=agendaViewMode==="day"?"":weekdays.map(d=>'<div class="calendar-weekday">'+d+'</div>').join("");
 for(const date of dates){
  const k=key(date),inMonth=agendaViewMode!=="month"||date.getMonth()===calendarMonth.getMonth(),isToday=k===today(),isSelected=k===agendaDate;
  const colors=[...new Set(forDate(date).map(categoryColor))];
  const dots=colors.length?'<span class="event-dots">'+colors.map(color=>'<i class="event-dot" style="background:'+color+'"></i>').join("")+'</span>':"";
  out+='<button class="calendar-day '+(!inMonth?"outside ":"")+(isToday?"today ":"")+(isSelected?"selected":"")+'" data-calendar-date="'+k+'" aria-label="'+esc(date.toLocaleDateString("pt-BR"))+'">'+date.getDate()+dots+'</button>';
 }
 $("calendarTitle").textContent=title;
 $("calendarGrid").innerHTML=out;
 $("selectedDateLabel").textContent=selectedDate.toLocaleDateString("pt-BR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
 document.querySelectorAll("[data-agenda-view]").forEach(button=>button.classList.toggle("active",button.dataset.agendaView===agendaViewMode));
}
function renderHomeReminder(){const root=$("homeReminderCard");if(!root)return;const now=new Date(),items=[];for(let offset=0;offset<8;offset++){const d=new Date(now.getFullYear(),now.getMonth(),now.getDate()+offset);for(const item of forDate(d)){if(!item.time)continue;const at=new Date(key(d)+"T"+item.time+":00");if(at.getTime()>=now.getTime())items.push({item,at,date:key(d)});}}items.sort((a,b)=>a.at-b.at);const next=items.slice(0,3);root.innerHTML='<div class="home-reminder-card"><div class="home-reminder-kicker">PRÓXIMOS COMPROMISSOS</div><div class="home-reminder-title">'+(next.length?'Sua agenda está organizada':'Nenhum compromisso próximo')+'</div>'+(next.length?next.map(x=>'<div class="home-reminder-line">'+(x.item.emoji||suggestEventEmoji(x.item.title,x.item.kind,x.item.dimension))+' <strong>'+esc(x.item.title)+'</strong> · '+(x.date===today()?'Hoje':parse(x.date).toLocaleDateString("pt-BR",{weekday:"short",day:"2-digit",month:"2-digit"}))+' às '+esc((x.item.time||"")+(x.item.endTime?" – "+x.item.endTime:""))+(x.item.schedule==="daily"?" · Diário":fixed(x.item)?" · Fixo semanal":"")+'</div>').join(""):'<div class="home-reminder-line">Cadastre um compromisso único ou fixo na agenda.</div>')+'</div>';}
function renderAgenda(){
 const list=forDate(parse(agendaDate));$("agendaDate").value=agendaDate;renderCalendar();
 $("agendaList").innerHTML=list.length?list.map(x=>{const o=occurrence(x,agendaDate),c=categoryColor(x),em=x.emoji||suggestEventEmoji(x.title,x.kind,x.dimension);return '<div class="item agenda-event '+(o.done?"done":"")+'" style="--event-color:'+c+'"><div class="agenda-event-main"><button class="item-action" data-answer="'+esc(x.id)+'" data-date="'+agendaDate+'"><strong><span class="event-emoji">'+em+'</span> '+esc(x.title)+'</strong><div class="muted">'+esc((x.time||"Sem horário")+(x.endTime?" – "+x.endTime:""))+' · '+esc(x.dimension||"Cotidiano")+' · '+esc(x.kind||"Compromisso")+' · '+(x.schedule==="daily"?"Diário":fixed(x)?"Fixo semanal":"Único")+'</div>'+(x.description?'<div class="muted">'+esc(x.description)+'</div>':'')+(x.location?'<div class="muted">Local: '+esc(x.location)+'</div>':'')+'<div class="statusline">'+(o.done?"✓ Concluído":o.note?"Não concluído — "+esc(o.note):"Toque para confirmar")+'</div></button></div><div class="agenda-event-actions"><button class="link" data-edit="'+esc(x.id)+'">Editar</button><button class="link" data-del="'+esc(x.id)+'">Excluir</button></div></div>'}).join(""):'<div class="empty">Nenhum compromisso cadastrado nesta data. Use “+ Compromisso” para criar compromissos únicos, diários ou semanais.</div>';
}
function modal(x,presetKind){
 const old=x||{title:"",description:"",location:"",endTime:"",reminders:[120,60,30],kind:presetKind||"Compromisso",dimension:"Cotidiano",date:agendaDate,time:"09:00",schedule:presetKind==="Hábito"?"daily":"once"};
 const initialEmoji=old.emoji||suggestEventEmoji(old.title,old.kind,old.dimension);
 $("modal").innerHTML='<div class="modalbg"><div class="modal"><div class="row between"><h2>'+(x?"Editar":"Novo")+(old.kind==="Hábito"?" hábito":" compromisso")+'</h2><button class="link" id="close">Fechar ✕</button></div><form id="edit"><label>Título</label><input class="field" name="title" id="eventTitle" required value="'+esc(old.title)+'"><label>Descrição</label><textarea class="field" name="description" id="eventDescription" rows="2" placeholder="Detalhes, objetivo ou orientação">'+esc(old.description||"")+'</textarea><label>Categoria</label><select class="field" name="dimension">'+areas.map(a=>'<option>'+a+'</option>').join("")+'</select><div class="event-emoji-picker"><label>Ícone do compromisso</label><select class="field" name="emoji" id="eventEmoji">'+eventEmojiOptions.map(e=>'<option value="'+e+'">'+e+'</option>').join("")+'</select><small>Ícone sugerido automaticamente. Você pode alterar.</small></div><label>Local (opcional)</label><input class="field" name="location" id="eventLocation" value="'+esc(old.location||"")+'" placeholder="Endereço ou local"><label>Classificação</label><select class="field" name="kind"><option>Compromisso</option><option>Hábito</option><option>Tarefa</option><option>Prazo</option><option>Evento</option><option>Atividade da igreja</option></select><label>Frequência</label><select class="field" name="schedule" id="scheduleMode"><option value="once">Uma única vez</option><option value="daily">Diariamente</option><option value="weekly">Fixo semanal — repetir nos dias escolhidos</option></select><div id="weeklyFields" class="hidden"><label>Repetir em quais dias?</label><div class="weekday-picker"><label><input type="checkbox" name="weekdays" value="0"> Dom</label><label><input type="checkbox" name="weekdays" value="1"> Seg</label><label><input type="checkbox" name="weekdays" value="2"> Ter</label><label><input type="checkbox" name="weekdays" value="3"> Qua</label><label><input type="checkbox" name="weekdays" value="4"> Qui</label><label><input type="checkbox" name="weekdays" value="5"> Sex</label><label><input type="checkbox" name="weekdays" value="6"> Sáb</label></div><label>Repetir até (opcional)</label><input class="field" type="date" name="endDate"><small>Cadastre uma vez. O compromisso aparecerá automaticamente em cada dia selecionado.</small></div><fieldset class="reminder-options"><legend>Lembretes deste compromisso</legend><label><input type="checkbox" name="reminders" value="120"> 2 horas antes</label><label><input type="checkbox" name="reminders" value="60"> 1 hora antes</label><label><input type="checkbox" name="reminders" value="30"> 30 minutos antes</label></fieldset><label>Data</label><input class="field" type="date" name="date" required value="'+esc(old.date||agendaDate)+'"><label>Horário de início</label><input class="field" type="time" name="time" value="'+esc(old.time||"09:00")+'"><label>Horário de término (opcional)</label><input class="field" type="time" name="endTime" value="'+esc(old.endTime||"")+'"><button class="btn gold">Salvar '+(old.kind==="Hábito"?"hábito":"compromisso")+'</button></form></div></div>';
 $("edit").kind.value=old.kind||"Compromisso";$("edit").dimension.value=area(old.dimension);$("eventEmoji").value=eventEmojiOptions.includes(initialEmoji)?initialEmoji:"📅";$("scheduleMode").value=old.schedule==="daily"?"daily":fixed(old)?"weekly":"once";$("weeklyFields").classList.toggle("hidden",$("scheduleMode").value!=="weekly");$("edit").querySelectorAll('[name="weekdays"]').forEach(el=>el.checked=(old.weekdays||[]).includes(Number(el.value)));$("edit").querySelectorAll('[name="reminders"]').forEach(el=>el.checked=(old.reminders||[120,60,30]).includes(Number(el.value)));$("scheduleMode").onchange=()=>$("weeklyFields").classList.toggle("hidden",$("scheduleMode").value!=="weekly");
 const autoEmoji=()=>{$("eventEmoji").value=suggestEventEmoji($("eventTitle").value,$("edit").kind.value,$("edit").dimension.value)};
 $("eventTitle").addEventListener("input",autoEmoji);$("edit").kind.onchange=()=>{autoEmoji();if($("edit").kind.value==="Hábito"&&$("scheduleMode").value==="once"){$("scheduleMode").value="daily";$("weeklyFields").classList.add("hidden")}};$("edit").dimension.onchange=autoEmoji;
 $("close").onclick=()=>$("modal").innerHTML="";
 $("edit").onsubmit=e=>{e.preventDefault();const fd=new FormData(e.target),schedule=fd.get("schedule"),weekly=schedule==="weekly",daily=schedule==="daily",weekdays=fd.getAll("weekdays").map(Number),v={title:fd.get("title").trim(),description:(fd.get("description")||"").trim(),location:(fd.get("location")||"").trim(),endTime:fd.get("endTime")||"",reminders:fd.getAll("reminders").map(Number),kind:fd.get("kind"),dimension:fd.get("dimension"),emoji:fd.get("emoji"),time:fd.get("time")||"",schedule:daily?"daily":weekly?"weekly":"once",category:"extra",date:fd.get("date"),endDate:(weekly||daily)?(fd.get("endDate")||""):"",weekdays:weekly?weekdays:[],completions:old.completions||{}};if(weekly&&!weekdays.length)return alert("Escolha ao menos um dia da semana para este compromisso fixo.");if(v.time&&v.endTime&&v.endTime<=v.time)return alert("O horário de término deve ser posterior ao início.");
 if(x)Object.assign(x,v);else db.items.push({id:uid(),...v});save();$("modal").innerHTML="";renderAgenda();renderEvolution();renderHabits()};
}
function answer(x,k){const o=occurrence(x,k);$("modal").innerHTML='<div class="modalbg"><div class="modal"><div class="eyebrow">Confirmação</div><h2>'+esc(x.title)+'</h2><p class="muted">'+fmt(k)+' · '+esc((x.time||"Sem horário")+(x.endTime?" – "+x.endTime:""))+'</p><p>'+(x.kind==="Hábito"?"Você concluiu este hábito?":"Você realizou este compromisso?")+'</p><label>Observação (opcional)</label><textarea id="note" class="field" rows="3" placeholder="Como foi ou o que impediu?">'+esc(o.note||"")+'</textarea><div class="row"><button class="btn gold" id="done">Sim, concluído</button><button class="btn" id="notdone">Não concluído</button></div><button class="link" id="cancel">Voltar</button></div></div>';$("done").onclick=()=>saveAnswer(x,k,true);$("notdone").onclick=()=>saveAnswer(x,k,false);$("cancel").onclick=()=>$("modal").innerHTML=""}
function saveAnswer(x,k,done){x.completions=x.completions||{};x.completions[k]={done,note:$("note").value.trim(),at:Date.now()};save();$("modal").innerHTML="";renderAgenda();renderEvolution();renderHabits();renderDashboard()}
const journalQuestions={
"Espiritual":[["scripture","Qual passagem bíblica leu ou estudou?"],["learning","O que compreendeu no contexto?"],["practice","Que obediência concreta vai praticar?"]],
"Matrimonial":[["event","O que aconteceu entre vocês, sem acusações ou rótulos?"],["feeling","O que sentiu e do que precisava naquele momento?"],["responsibility","Qual parte da situação está sob sua responsabilidade?"],["repair","Qual conversa, atitude de cuidado ou reparação será seu próximo passo?"]],
"Profissional":[["goal","Qual resultado profissional está buscando?"],["obstacle","O que está impedindo seu avanço ou mantendo você na comodidade?"],["challenge","Qual ação desconfortável, mas responsável, precisa executar?"],["deadline","Quando fará isso e como saberá que concluiu?"]],
"Cotidiano":[["wellbeing","Como você avalia seu bem-estar hoje?","select",["Não registrei","Bem","Estável","Sobrecarregado","Preciso de apoio"]],["exercise","Você praticou atividade física hoje?","select",["Não registrei","Pratiquei","Não pratiquei"]],["exerciseDetail","Qual atividade e por quantos minutos?"],["food","Como foi sua alimentação hoje?","select",["Não registrei","Equilibrada","Irregular","Precisa de atenção"]],["meals","Conseguiu respeitar horários de refeição?","select",["Não registrei","Sim","Parcialmente","Não"]],["sleep","Quantas horas dormiu e como foi o descanso?"],["leisure","Teve lazer ou descanso de qualidade?"],["distraction","Qual distração ou hábito precisa limitar?"]]
};
function renderJournalPrompts(){
 const dimension=$("jd").value,questions=journalQuestions[dimension]||[];
 $("journalPrompts").innerHTML='<div class="area"><div class="eyebrow">Exame orientado · '+dimension+'</div><p class="muted">Responda o que fizer sentido. O aplicativo separará seus registros de qualquer interpretação ou recomendação.</p>'+questions.map(([key,label,type,options])=>'<label>'+label+'</label>'+(type==="select"?'<select class="field" data-jprompt="'+key+'">'+options.map(v=>'<option>'+v+'</option>').join("")+'</select>':'<textarea class="field" data-jprompt="'+key+'" rows="2" placeholder="Registre sua resposta..."></textarea>')).join("")+'</div>';
}
let editingProfessionalId=null;
function renderProfessional(){
 const root=$("professionalList");if(!root)return;
 const entries=db.journal.filter(x=>area(x.dimension)==="Profissional").sort((a,b)=>b.at-a.at);
 root.innerHTML=entries.length?entries.map(x=>'<article class="item"><div class="grow"><b>'+esc(x.title||"Registro profissional")+'</b><div class="muted">'+new Date(x.at).toLocaleString("pt-BR")+'</div><p>'+esc(x.body||"Registro profissional")+'</p></div><div class="row"><button class="link" data-professional-edit="'+esc(x.id)+'">Editar</button><button class="link" data-professional-del="'+esc(x.id)+'">Excluir</button></div></article>').join(""):'<div class="empty">Nenhum registro profissional. Registre uma meta, decisão, aprendizado ou próximo passo verificável.</div>';
 const form=$("professionalForm");
 form.onsubmit=e=>{e.preventDefault();const title=$("professionalTitle").value.trim(),body=$("professionalBody").value.trim();if(!title||!body)return alert("Informe o título e o registro.");if(editingProfessionalId){const entry=db.journal.find(x=>x.id===editingProfessionalId);if(entry){entry.title=title;entry.body=body;entry.at=Date.now();entry.dayKey=today()}}else db.journal.push({id:uid(),dimension:"Profissional",title,body,at:Date.now(),dayKey:today()});editingProfessionalId=null;form.reset();$("professionalSave").textContent="Salvar registro profissional";save();renderProfessional();renderDiary();renderDashboard();};
 root.querySelectorAll("[data-professional-edit]").forEach(button=>button.onclick=()=>{const entry=db.journal.find(x=>x.id===button.dataset.professionalEdit);if(!entry)return;editingProfessionalId=entry.id;$("professionalTitle").value=entry.title||"";$("professionalBody").value=entry.body||"";$("professionalSave").textContent="Atualizar registro";$("professionalTitle").focus()});
 root.querySelectorAll("[data-professional-del]").forEach(button=>button.onclick=()=>{if(!confirm("Excluir este registro profissional?"))return;db.journal=db.journal.filter(x=>x.id!==button.dataset.professionalDel);save();renderProfessional();renderDiary();renderDashboard()});
}
function renderDiary(){$("journalList").innerHTML=db.journal.slice().reverse().map(x=>'<div class="item"><div class="grow"><b>'+esc(x.title||"Relato")+'</b><div class="muted">'+esc(x.dimension)+' · '+new Date(x.at).toLocaleString("pt-BR")+'</div><p>'+esc(x.body||"Registro orientado")+'</p>'+(x.checkin?'<div class="muted">'+Object.entries(x.checkin).filter(([,v])=>v).map(([k,v])=>'<p><b>'+esc((journalQuestions[x.dimension]||[]).find(q=>q[0]===k)?.[1]||k)+':</b> '+esc(v)+'</p>').join("")+'</div>':'')+'</div></div>').join("")||'<div class="empty">Nenhum relato registrado.</div>';renderJournalPrompts()}
function renderIntel(){
 const latestByArea=areas.map(a=>({area:a,record:db.journal.filter(x=>area(x.dimension)===a).sort((x,y)=>y.at-x.at)[0]}));
 const content=latestByArea.map(({area:a,record})=>{
  if(!record)return '<article class="area"><h3>'+(a==="Espiritual"?"Devoção a Deus":a)+'</h3><p>Ainda não há relato nesta área. A inteligência não presumirá como você está nem inventará acontecimentos. Registre sua realidade no Diário para receber orientação contextual.</p></article>';
  const records=db.journal.filter(x=>area(x.dimension)===a).sort((x,y)=>y.at-x.at).slice(0,8),match=bestPattern(records,a),g=guidance[a],checkin=record.checkin||{},questions=journalQuestions[a]||[];
  const checkinHtml=Object.entries(checkin).filter(([,v])=>v).map(([k,v])=>'<p><b>'+esc(questions.find(q=>q[0]===k)?.[1]||k)+':</b> '+esc(v)+'</p>').join("");
  const coaching=a==="Matrimonial"?"Acompanhamento relacional: identifique fatos, sentimentos e necessidades; escute a perspectiva da sua esposa; reconheça sua parte sem assumir a responsabilidade pelo que pertence ao outro; combine um próximo passo concreto. Se houver violência, ameaça ou coerção, priorize segurança e apoio especializado.":a==="Profissional"?"Mentoria de desenvolvimento: defina resultado, prazo, recursos, obstáculos e uma ação verificável. Avalie riscos antes de decisões financeiras ou profissionais importantes.":a==="Cotidiano"?"Cuidado integral: observe emoções, sono, alimentação, movimento, descanso e organização. Diferencie fato, interpretação e sentimento. Não transforme ausência de registro em conclusão. Sintomas persistentes ou preocupantes merecem avaliação de saúde.":"Devoção a Deus: observe o texto bíblico no contexto, examine motivações e transforme a verdade compreendida em obediência. Disciplina não é desempenho para impressionar.";
  return '<article class="reading-page"><div class="eyebrow">Acompanhamento · '+(a==="Espiritual"?"Devoção a Deus":a)+'</div><h3>'+esc(record.title||"Relato mais recente")+'</h3><p>'+esc(record.body||"")+'</p>'+(checkinHtml?'<div class="reading-topic"><b>Informações que você registrou</b>'+checkinHtml+'</div>':"")+(match?'<div class="goldtext">'+esc(match.ref)+'</div><h3>'+esc(match.name)+'</h3><p>'+esc(match.advice)+'</p><p><b>Aplicação:</b> '+esc(match.step)+'</p>':'<div class="goldtext">'+esc(g.ref)+'</div><p>'+esc(g.principle)+'</p><p><b>Aplicação:</b> '+esc(g.action)+'</p>')+'<div class="reading-topic"><b>Plano de acompanhamento</b><p>'+coaching+'</p></div></article>';
 }).join("");
 $("intelligenceAreas").innerHTML=content+'<p class="muted" style="margin-top:12px">A leitura contextual local continua disponível sem internet. Para perguntas abertas e análise generativa integrada, use a assistente MEtanoia AI acima: ela só envia os registros recentes selecionados quando você autoriza explicitamente. A IA não faz diagnóstico e não substitui psicoterapia, avaliação médica ou acompanhamento pastoral.</p>';
}
function dashboardWindow(days,offset=0){const end=new Date();end.setHours(23,59,59,999);end.setDate(end.getDate()-offset*days);const start=new Date(end);start.setHours(0,0,0,0);start.setDate(start.getDate()-days+1);return {start,end,startKey:key(start),endKey:key(end)}}
function dashboardOccurrences(dimension,window){let planned=0,done=0;const windowDays=Math.round((window.end-window.start)/86400000)+1;for(const item of db.items.filter(x=>area(x.dimension)===dimension)){if(fixed(item)){for(let n=0;n<windowDays;n++){const d=new Date(window.start);d.setDate(d.getDate()+n);if(occursOn(item,d)){planned++;if(occurrence(item,key(d)).done)done++;}}}else if(item.date>=window.startKey&&item.date<=window.endKey){planned++;if(occurrence(item,item.date).done)done++;}}return {planned,done};}
function dashboardRate(n,d){return d?Math.round(n/d*100):null}
function renderDashboard(){
 const days=dashboardPeriod||7,window=dashboardWindow(days),previous=dashboardWindow(days,1);$("dashPeriodLabel").textContent="Últimos "+days+" dias";
 const entries=db.journal.filter(x=>x.at>=window.start.getTime()&&x.at<=window.end.getTime()),prevEntries=db.journal.filter(x=>x.at>=previous.start.getTime()&&x.at<=previous.end.getTime());
 const valid=(records,keyName,allowed)=>records.map(x=>x.checkin?.[keyName]).filter(v=>v&&v!=="Não registrei"&&allowed.includes(v));
 const ex=valid(entries,"exercise",["Pratiquei","Não pratiquei"]),food=valid(entries,"food",["Equilibrada","Irregular","Precisa de atenção"]),well=valid(entries,"wellbeing",["Bem","Estável","Sobrecarregado","Preciso de apoio"]);
 const pex=valid(prevEntries,"exercise",["Pratiquei","Não pratiquei"]),pfood=valid(prevEntries,"food",["Equilibrada","Irregular","Precisa de atenção"]),pwell=valid(prevEntries,"wellbeing",["Bem","Estável","Sobrecarregado","Preciso de apoio"]);
 const metrics=[
  {name:"Desenvolvimento pessoal",type:"personal",desc:"Reflexões e revisões registradas; sem meta arbitrária para converter quantidade em nota."},
  {name:"Bem-estar emocional",type:"well",value:well.filter(v=>v==="Bem"||v==="Estável").length,base:well.length,prev:pwell.filter(v=>v==="Bem"||v==="Estável").length,prevBase:pwell.length,desc:"Autoavaliações positivas entre as respostas válidas."},
  {name:"Exercícios",type:"rate",value:ex.filter(v=>v==="Pratiquei").length,base:ex.length,prev:pex.filter(v=>v==="Pratiquei").length,prevBase:pex.length,desc:"Dias com prática entre respostas conclusivas."},
  {name:"Alimentação",type:"rate",value:food.filter(v=>v==="Equilibrada").length,base:food.length,prev:pfood.filter(v=>v==="Equilibrada").length,prevBase:pfood.length,desc:"Registros classificados como alimentação equilibrada."},
  {name:"Devoção",type:"dimension",dim:"Espiritual",desc:"Compromissos e hábitos espirituais concluídos."},
  {name:"Matrimônio",type:"dimension",dim:"Matrimonial",desc:"Compromissos e hábitos matrimoniais concluídos."},
  {name:"Profissional",type:"dimension",dim:"Profissional",desc:"Compromissos e hábitos profissionais concluídos."},
  {name:"Cotidiano",type:"dimension",dim:"Cotidiano",desc:"Compromissos e hábitos cotidianos concluídos."}
 ];
 const results=metrics.map(m=>{if(m.type==="personal"){const count=entries.length+db.reviews.filter(x=>x.at>=window.start.getTime()&&x.at<=window.end.getTime()).length,prevCount=prevEntries.length+db.reviews.filter(x=>x.at>=previous.start.getTime()&&x.at<=previous.end.getTime()).length;return {...m,value:count,base:count,prev:prevCount,prevBase:prevCount,pct:null,prevPct:null}}if(m.type==="dimension"){const a=dashboardOccurrences(m.dim,window),b=dashboardOccurrences(m.dim,previous);return {...m,value:a.done,base:a.planned,prev:b.done,prevBase:b.planned,pct:dashboardRate(a.done,a.planned),prevPct:dashboardRate(b.done,b.planned)}}const pct=dashboardRate(m.value,m.base),prevPct=dashboardRate(m.prev,m.prevBase);return {...m,pct,prevPct}});
 $("dashboardMetrics").innerHTML=results.map((m,i)=>{const pct=m.pct,delta=pct!==null&&m.prevPct!==null?pct-m.prevPct:null;const progress=pct===null?0:pct;return '<article class="dash-metric"><div class="dash-metric-top"><h3>'+m.name+'</h3><div class="dash-ring" style="--progress:'+progress*3.6+'deg"><strong>'+(pct===null?'—':pct+'%')+'</strong></div></div><p class="dash-evidence">'+(m.type==="personal"?(m.base?m.base+' reflexão(ões)/revisão(ões) registrada(s)':"Ainda sem registros"):(m.base?m.value+' de '+m.base+' respostas/ocorrências válidas':"Ainda sem registros suficientes"))+'. '+m.desc+'</p><div class="dash-trend">'+(delta===null?'Comparação indisponível':(delta>0?'▲ ':delta<0?'▼ ':'• ')+Math.abs(delta)+' p.p. vs. período anterior')+'</div><button type="button" data-dash-area="'+(m.dim||"Cotidiano")+'">Abrir registros →</button></article>'}).join("");
 const dimensionRates=results.filter(x=>x.type==="dimension"&&x.pct!==null),strong=dimensionRates.filter(x=>x.pct>=75),attention=dimensionRates.filter(x=>x.pct<50);
 const insight=[];
 if(strong.length)insight.push('<div class="dash-insight"><b>Constância observada</b><p>'+strong.map(x=>x.name).join(", ")+': '+strong.map(x=>x.pct+'%').join(", ")+' de conclusões nas ocorrências programadas.</p></div>');
 if(attention.length)insight.push('<div class="dash-insight"><b>Áreas que merecem revisão</b><p>'+attention.map(x=>x.name).join(", ")+': os compromissos programados tiveram menor proporção de conclusão neste período. Isso é um sinal para revisar metas e contexto, não um julgamento pessoal.</p></div>');
 if(!dimensionRates.length)insight.push('<div class="dash-insight"><b>Vamos formar uma linha de base</b><p>Cadastre compromissos ou hábitos com dimensão e registre as conclusões. O painel não atribui nota sem ocorrências mensuráveis.</p></div>');
 const wellbeing=results.find(x=>x.type==="well");if(!wellbeing.base)insight.push('<div class="dash-insight"><b>Bem-estar ainda sem base</b><p>Nos próximos registros do Diário, a autoavaliação de bem-estar ficará disponível. Nenhuma inferência clínica será feita.</p></div>');
 insight.push('<div class="dash-insight"><b>Próximo passo</b><p>Revise uma meta por vez e registre o que realmente aconteceu. A comparação considera o período anterior de igual duração; dias sem registro não entram como resposta negativa.</p></div>');
 $("dashInsights").innerHTML=insight.join("");
 const recordedDays=new Set(entries.map(x=>new Date(x.at).toDateString())).size;
 $("dashSummary").innerHTML='<strong>'+recordedDays+' dia(s) com registros</strong><span>· '+entries.length+' entrada(s) no diário no período. Os percentuais aparecem apenas quando há denominador real.</span>';
}
let dashboardPeriod=7;

function renderEvolution(){
 const weekAgo=Date.now()-7*86400000,weekEntries=db.journal.filter(x=>x.at>=weekAgo),routine=weekEntries.filter(x=>area(x.dimension)==="Cotidiano"&&x.checkin),exercise=routine.map(x=>x.checkin.exercise).filter(Boolean),practiced=exercise.filter(x=>x==="Pratiquei").length,notPracticed=exercise.filter(x=>x==="Não pratiquei").length,unrecorded=exercise.filter(x=>x==="Não registrei").length,food=routine.map(x=>x.checkin.food).filter(Boolean),foodAlerts=food.filter(x=>x==="Irregular"||x==="Precisa de atenção").length;
 const weekly='<article class="area"><div class="eyebrow">Relatório dos últimos 7 dias</div><h3>Rotina e hábitos</h3><p>'+routine.length+' check-in(s) cotidiano(s) registrados.</p><p>Atividade física: '+practiced+' dia(s) com prática registrada · '+notPracticed+' dia(s) sem prática declarada · '+unrecorded+' sem informação conclusiva.</p><p>Alimentação: '+foodAlerts+' registro(s) sinalizaram irregularidade ou atenção.</p><p class="muted">Este relatório considera apenas os registros enviados. Ausência de registro não significa ausência de atividade. Defina um desafio realista para a próxima semana e registre o que realmente aconteceu.</p></article>';
 const html=areas.map(a=>{const entries=db.journal.filter(x=>area(x.dimension)===a).length,items=db.items.filter(x=>area(x.dimension)===a);let total=0,done=0;for(const x of items){if(fixed(x)){for(let i=0;i<7;i++){const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-6+i);if(occursOn(x,d)){total++;if(occurrence(x,key(d)).done)done++}}}else if(x.date&&x.date>=key(new Date(Date.now()-6*86400000))&&x.date<=today()){total++;if(occurrence(x,x.date).done)done++}}
 return '<article class="area"><h3>'+a+'</h3><p>'+entries+' relato(s) no diário</p><p>'+done+' de '+total+' ocorrências recentes confirmadas em compromissos</p><p class="muted">Esses dados descrevem registros, não definem seu crescimento espiritual ou pessoal.</p></article>'}).join("");
 $("evolutionAreas").innerHTML=weekly+html;$("reviewList").innerHTML=db.reviews.slice().reverse().map(x=>'<div class="item"><div class="grow"><b>Revisão · '+new Date(x.at).toLocaleDateString("pt-BR")+'</b><p>'+esc(x.body)+'</p></div></div>').join("")
}
function renderHabits(){
 const root=$("habitsList");if(!root)return;
 const habits=db.items.filter(x=>x.kind==="Hábito").sort((a,b)=>(a.title||"").localeCompare(b.title||""));
 if(!habits.length){root.innerHTML='<div class="empty">Nenhum hábito cadastrado. Crie um hábito diário ou semanal e acompanhe as conclusões sem perder o histórico.</div>'}
 else{
  root.innerHTML=habits.map(x=>{
   let planned=0,done=0;
   for(let i=0;i<7;i++){const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-6+i);if(occursOn(x,d)){planned++;if(occurrence(x,key(d)).done)done++}}
   const pct=planned?Math.round(done/planned*100):0,frequency=x.schedule==="daily"?"Diário":fixed(x)?"Semanal":"Único";
   return '<article class="item"><div class="grow"><b>'+esc(x.title)+'</b><p>'+esc(x.dimension||"Cotidiano")+' · '+frequency+' · '+esc(x.time||"Sem horário")+'</p><p class="muted">Últimos 7 dias: '+done+'/'+planned+' conclusões · '+pct+'% de consistência registrada</p></div><div class="row"><button class="link" data-answer="'+esc(x.id)+'" data-date="'+today()+'">Registrar hoje</button><button class="link" data-edit="'+esc(x.id)+'">Editar</button><button class="link" data-del="'+esc(x.id)+'">Excluir</button></div></article>'
  }).join("")
 }
 const add=$("newHabit");if(add)add.onclick=()=>modal(null,"Hábito");
}
function renderReminderStatus(){if(!("Notification"in window)){$("reminderStatus").textContent="Este navegador não oferece notificações.";return}const p=Notification.permission;$("reminderStatus").textContent=p==="granted"?"Permissão concedida. A PWA verifica lembretes enquanto estiver aberta ou ativa neste navegador.":"Permissão de notificações: "+p+". Os lembretes só podem ser exibidos depois da autorização."; $("enableReminders").textContent=p==="granted"?"Notificações ativadas":"Ativar notificações";$("enableReminders").disabled=p==="granted"}
async function enableReminders(){if(!("Notification"in window))return alert("Navegador sem suporte.");const p=await Notification.requestPermission();renderReminderStatus();if(p==="granted"&&"serviceWorker"in navigator){const reg=await navigator.serviceWorker.ready;await reg.showNotification("MEtonoia — lembretes ativados",{body:"Lembretes diários às 07h, 12h e 18h; compromissos 2h, 1h e 30min antes.",icon:"/icons/metonoia-symbol.webp",tag:"metonoia-activated"})}}
async function checkCommitmentReminders(){
 if(!("Notification"in window)||Notification.permission!=="granted"||!("serviceWorker"in navigator))return;
 const now=Date.now();
 for(const item of db.items){
  const offsets=Array.isArray(item.reminders)?item.reminders:[120,60,30];
  if(!item.date||!item.time)continue;
  const occurrenceDate=fixed(item)?today():item.date;if(fixed(item)&&!occursOn(item,parse(occurrenceDate)))continue;
  const eventAt=new Date(occurrenceDate+"T"+item.time+":00").getTime();
  if(!Number.isFinite(eventAt)||eventAt<=now)continue;
  for(const minutes of offsets){
   const fireAt=eventAt-minutes*60000,stamp="metonoia-event-"+item.id+"-"+occurrenceDate+"-"+minutes;
   if(now<fireAt||now>=fireAt+60000||localStorage.getItem(stamp))continue;
   localStorage.setItem(stamp,"1");
   const label=minutes===120?"em 2 horas":minutes===60?"em 1 hora":"em 30 minutos";
   try{const reg=await navigator.serviceWorker.ready;await reg.showNotification("MEtanoia · "+(item.kind==="Hábito"?"Hábito":fixed(item)?"Compromisso fixo":"Compromisso próximo"),{body:item.title+" começa "+label+".",icon:"/icons/metonoia-symbol.webp",badge:"/icons/metonoia-symbol.webp",image:"/icons/metonoia-symbol.webp",tag:stamp,renotify:false,vibrate:[250,120,250],requireInteraction:true})}catch(e){console.warn(e)}
  }
 }
}
async function checkReminderClock(){if(!("Notification"in window)||Notification.permission!=="granted")return;await checkCommitmentReminders();const n=new Date(),h=n.getHours();if(n.getMinutes()!==0||![7,12,18].includes(h))return;const stamp=key(n)+"-"+h;if(localStorage.getItem("metonoia-reminder-"+stamp))return;localStorage.setItem("metonoia-reminder-"+stamp,"1");const dailyBody=h===7?(ingridUnlocked?ingridDailyUpdate(ingridData).challenge:"Comece o dia com escuta: pergunte à Ingrid como está e o que seria cuidado para ela hoje."):h===12?(ingridUnlocked?("Reavalie com delicadeza: "+ingridDailyUpdate(ingridData).challenge):"Faça uma pausa e confira se há alguma necessidade prática que você pode aliviar."):(ingridUnlocked?("Feche o dia sem cobranças: "+ingridDailyUpdate(ingridData).challenge):"Encerre o dia com gratidão, escuta e uma atitude concreta de carinho.");const opts={body:dailyBody,icon:"/icons/metonoia-symbol.webp",badge:"/icons/metonoia-symbol.webp",tag:"metonoia-daily-"+stamp,renotify:false,vibrate:[250,120,250]};try{const reg=await navigator.serviceWorker.ready;await reg.showNotification("MEtonoia · Hora de revisar o cotidiano",opts)}catch(e){console.warn(e)}}

const INGRID_VAULT_KEY="metonoia-ingrid-vault-v1";
let ingridData=null,ingridUnlocked=false,ingridSessionPin='',ingridVaultSalt=null;
const toB64=bytes=>{const data=new Uint8Array(bytes);let binary="";for(let i=0;i<data.length;i+=32768)binary+=String.fromCharCode(...data.subarray(i,i+32768));return btoa(binary)};
const fromB64=value=>Uint8Array.from(atob(value),c=>c.charCodeAt(0));
async function ingridCryptoKey(pin,salt){
 const material=await crypto.subtle.importKey("raw",new TextEncoder().encode(pin),"PBKDF2",false,["deriveKey"]);
 return crypto.subtle.deriveKey({name:"PBKDF2",salt,iterations:310000,hash:"SHA-256"},material,{name:"AES-GCM",length:256},false,["encrypt","decrypt"]);
}
async function saveIngridVault(){
 if(!ingridData)return;
 if(!ingridSessionPin)throw new Error("O cofre está bloqueado. Desbloqueie-o antes de salvar.");
 if(!ingridVaultSalt)ingridVaultSalt=crypto.getRandomValues(new Uint8Array(16));
 const key=await ingridCryptoKey(ingridSessionPin,ingridVaultSalt),iv=crypto.getRandomValues(new Uint8Array(12));
 const plaintext=new TextEncoder().encode(JSON.stringify(ingridData));
 const ciphertext=await crypto.subtle.encrypt({name:"AES-GCM",iv},key,plaintext);
 localStorage.setItem(INGRID_VAULT_KEY,JSON.stringify({
  version:3,encrypted:true,algorithm:"AES-256-GCM",kdf:"PBKDF2-SHA256",iterations:310000,
  salt:toB64(ingridVaultSalt),iv:toB64(iv),ciphertext:toB64(ciphertext),updatedAt:new Date().toISOString()
 }));
}
async function unlockIngridVault(){
 if(!$('ingridConsent')?.checked)return alert('Só prossiga depois de Ingrid autorizar expressamente o registro e o uso dessas informações.');
 const raw=localStorage.getItem(INGRID_VAULT_KEY);
 if(raw){
  let saved;
  try{saved=JSON.parse(raw)}catch{return alert('O arquivo local do cofre está inválido. Nenhum dado foi alterado.')}
  if(saved.version===3&&saved.encrypted===true){
   const pin=prompt('Digite a senha do cofre matrimonial. Ela não será enviada à nuvem.');
   if(!pin)return;
   try{
    const salt=fromB64(saved.salt),iv=fromB64(saved.iv),key=await ingridCryptoKey(pin,salt);
    const plaintext=await crypto.subtle.decrypt({name:"AES-GCM",iv},key,fromB64(saved.ciphertext));
    const parsed=JSON.parse(new TextDecoder().decode(plaintext));
    if(!parsed||typeof parsed!=="object")throw new Error("Dados inválidos");
    ingridData=parsed;ingridSessionPin=pin;ingridVaultSalt=salt;ingridUnlocked=true;renderIngrid();return;
   }catch{return alert('Senha incorreta ou dados criptografados inválidos. O cofre permaneceu bloqueado e nenhum dado foi alterado.')}
  }
  if(saved.encrypted===false&&saved.data&&typeof saved.data==="object"){
   const pin=prompt('Os registros existentes estão sem criptografia. Para preservá-los e protegê-los, crie agora uma senha com pelo menos 10 caracteres. Não a perca: não há recuperação sem ela.');
   if(!pin||pin.length<10)return alert('A senha precisa ter pelo menos 10 caracteres. Os registros antigos continuam preservados.');
   const confirmPin=prompt('Confirme a nova senha do cofre.');
   if(pin!==confirmPin)return alert('As senhas não coincidem. Os registros antigos continuam preservados.');
   ingridData=saved.data;ingridSessionPin=pin;ingridVaultSalt=crypto.getRandomValues(new Uint8Array(16));
   try{await saveIngridVault();ingridUnlocked=true;renderIngrid();return}catch{ingridData=null;ingridSessionPin='';ingridVaultSalt=null;return alert('Não foi possível criptografar os registros. O conteúdo antigo permanece armazenado sem alterações.')}
  }
  return alert('Este cofre usa um formato antigo ou não reconhecido. Os dados foram preservados, mas não serão abertos nem apagados automaticamente. É necessária uma recuperação compatível antes de continuar.');
 }
 const pin=prompt('Crie uma senha para proteger o cofre matrimonial (mínimo de 10 caracteres). Ela não será enviada à nuvem e não poderá ser recuperada se for perdida.');
 if(!pin||pin.length<10)return alert('A senha precisa ter pelo menos 10 caracteres.');
 const confirmPin=prompt('Confirme a senha do cofre.');
 if(pin!==confirmPin)return alert('As senhas não coincidem.');
 ingridData={cycle:{lastPeriodStart:'',cycleLength:28,periodDays:5,regularity:'unknown'},entries:[],consentConfirmedAt:new Date().toISOString()};
 ingridSessionPin=pin;ingridVaultSalt=crypto.getRandomValues(new Uint8Array(16));
 try{await saveIngridVault();ingridUnlocked=true;renderIngrid()}catch{ingridData=null;ingridSessionPin='';ingridVaultSalt=null;return alert('Não foi possível proteger o cofre. Nenhum registro foi criado.')}
}
function resetIngridVault(){
 const accepted=confirm('Apagar definitivamente os dados da Ingrid armazenados neste navegador? Esta ação não pode ser desfeita.');
 if(!accepted)return;
 localStorage.removeItem(INGRID_VAULT_KEY);ingridData=null;ingridUnlocked=false;ingridSessionPin='';ingridVaultSalt=null;renderIngrid();
}
function dayOffset(date,days){const d=new Date(date+"T12:00:00");d.setDate(d.getDate()+days);return key(d)}
function cycleStatus(data){
 const c=data?.cycle||{};if(!c.lastPeriodStart)return null;
 const diff=Math.floor((new Date(today()+"T00:00:00Z")-new Date(c.lastPeriodStart+"T00:00:00Z"))/86400000);
 const length=Number(c.cycleLength)||28;if(diff<0)return null;
 const completed=Math.floor(diff/length),nextStart=dayOffset(c.lastPeriodStart,(completed+1)*length);
 const ovulation=dayOffset(nextStart,-14),fertileStart=dayOffset(ovulation,-5),cycleDay=(diff%length)+1;
 if(length<21||length>35)return {phase:"Ciclo fora da faixa habitual para adultas — previsão não exibida",cycleDay};
 if(c.regularity!=="regular")return {phase:c.regularity==="irregular"?"Ciclo irregular — previsão por calendário não confiável":"Regularidade ainda não confirmada — sem previsão confiável",cycleDay};
 let phase="Fase folicular estimada";
 if(cycleDay<=Number(c.periodDays||5))phase="Menstruação (data registrada)";
 else if(today()>=fertileStart&&today()<=ovulation)phase="Janela fértil estimada";
 else if(today()>ovulation)phase="Fase lútea estimada";
 return {phase,cycleDay,nextStart,ovulation,fertileStart,cycleLength:length};
}
function ingridChallenge(status,care){
 if(care?.conversation==="low"||care?.sensitivity==="yes")return "Hoje, priorize gentileza e presença sem cobrança. Não force conversa nem peça explicações. Ofereça ajuda prática e diga com carinho que está disponível; se ela der espaço, acolha e escute.";
 if(!status)return "Hoje, o desafio é simples: pergunte com delicadeza como Ingrid está e o que seria cuidado para ela. Respeite se preferir silêncio.";
 if(status.phase.startsWith("Menstruação"))return "Ofereça conforto prático e pergunte, sem insistir, se há algo que ajudaria. Não presuma dor, cansaço ou necessidade de descanso.";
 if(status.phase.startsWith("Janela fértil"))return "Demonstre carinho de acordo com o que ela deseja. Não crie expectativa sexual ou reprodutiva; intimidade exige vontade e consentimento mútuos.";
 if(status.phase.startsWith("Fase lútea"))return "Ofereça um check-in gentil e pergunte se há algo concreto que possa aliviar. Não atribua emoções ou reações aos hormônios.";
 return "Pergunte se há algo que ela gostaria de fazer ou planejar e ofereça parceria concreta, respeitando o ritmo dela.";
}
function ingridDailyUpdate(data){
 const status=cycleStatus(data),care=(data?.dailyCare||[]).find(x=>x.date===today());
 return {status,care,challenge:ingridChallenge(status,care),summary:care?.conversation==="low"||care?.sensitivity==="yes"?"A observação de hoje indica preferência por pouca conversa: presença gentil, sem pressão.":status?.phase||"Ciclo ainda sem previsão confiável"};
}
function renderIngrid(){
 const root=$("ingridContent");if(!root)return;
 if(!ingridUnlocked){
  const exists=!!localStorage.getItem(INGRID_VAULT_KEY);
  root.innerHTML='<div class="ingrid-profile"><img src="/icons/ingrid_avatar.webp" alt="Avatar ilustrado da Ingrid"><div><div class="eyebrow">Matrimônio · cuidado diário</div><h2>Ingrid</h2><p>Um lembrete visual para cultivar presença, carinho e parceria.</p></div></div><div class="daily-challenge"><div class="eyebrow">Desafio de cuidado de hoje</div><p>Pergunte à Ingrid como ela está e o que seria uma demonstração de cuidado para ela hoje. Escute a resposta sem tentar adivinhar.</p></div><div class="ingrid-lock"><div class="eyebrow">Cofre matrimonial protegido</div><p>Os registros são cifrados neste aparelho com AES-256-GCM e uma senha que permanece somente na sessão local. A senha não é enviada ao servidor. Se for esquecida, não será possível recuperar os dados sem uma cópia e a senha correta.</p><label class="consent-line"><input id="ingridConsent" type="checkbox"> Ingrid autorizou expressamente o registro e o uso destas informações no MEtanoia.</label><button id="ingridUnlock" class="btn gold">'+(exists?"Desbloquear cofre da Ingrid":"Criar cofre protegido da Ingrid")+'</button>'+(exists?'<p class="muted">Se os dados antigos estavam sem criptografia, o aplicativo pedirá uma nova senha e tentará protegê-los sem apagá-los.</p>':"")+'</div>';
  $("ingridUnlock").onclick=unlockIngridVault;return;
 }
 const c=ingridData.cycle||{},daily=ingridDailyUpdate(ingridData),status=daily.status,todayCare=daily.care,todayEntry=(ingridData.entries||[]).find(x=>x.date===today());
 const archives=(ingridData.archives||[]).slice().sort((a,b)=>b.archivedAt-a.archivedAt);
 const archiveHtml=archives.length?archives.slice(0,6).map(x=>'<article class="area"><b>Ciclo arquivado · '+esc(fmt(x.cycle.lastPeriodStart))+'</b><p>'+esc(x.cycle.cycleLength||"—")+' dias de duração média · sangramento informado: '+esc(x.cycle.periodDays||"—")+' dias · regularidade: '+esc(x.cycle.regularity||"não informada")+'</p><p class="muted">Arquivado em '+esc(fmt(new Date(x.archivedAt).toISOString().slice(0,10)))+'</p></article>').join(""):'<p class="muted">Nenhum ciclo anterior arquivado. Ao salvar uma nova data de início, o ciclo anterior será guardado aqui.</p>';
 const nextDate=status?.nextStart?fmt(status.nextStart):"sem previsão confiável";
 const recent=(ingridData.entries||[]).slice().sort((a,b)=>b.at-a.at).slice(0,5).map(x=>'<article class="area"><b>'+esc(fmt(x.date))+'</b>'+(x.shared?'<p><b>O que ela compartilhou:</b> '+esc(x.shared)+'</p>':"")+(x.request?'<p><b>O que ela pediu:</b> '+esc(x.request)+'</p>':"")+(x.response?'<p><b>Como você respondeu:</b> '+esc(x.response)+'</p>':"")+'</article>').join("");
 root.innerHTML='<div class="ingrid-profile"><img src="/icons/ingrid_avatar.webp" alt="Avatar ilustrado da Ingrid"><div><div class="eyebrow">Matrimônio · cuidado diário</div><h2>Ingrid</h2><p>Conhecê-la, ouvi-la e amá-la com atitudes concretas — não presumir o que ela sente.</p></div></div><div class="area"><div class="eyebrow">Ciclo menstrual</div><h3>'+esc(status?.phase||"Dados do ciclo ainda não cadastrados")+'</h3><p>'+(status?.cycleDay?'Dia '+status.cycleDay+' do ciclo · próxima menstruação estimada: '+esc(nextDate):(status?.phase||"Informe o primeiro dia da última menstruação para iniciar o acompanhamento."))+'</p>'+(status?.fertileStart?'<p>Janela fértil estimada: '+esc(fmt(status.fertileStart))+' a '+esc(fmt(status.ovulation))+' · ovulação estimada: '+esc(fmt(status.ovulation))+'</p>':"")+(c.observedFertileStart?'<p>Início da janela fértil percebido e informado: '+esc(fmt(c.observedFertileStart))+' (observação, não confirmação de ovulação).</p>':"")+(Number(c.periodDays)>7?'<p class="muted">Sangramento acima de 7 dias: vale conversar com ginecologista.</p>':"")+'<p class="muted">Estimativa por calendário, não confirmação de ovulação. Não use para evitar gravidez. O ciclo pode variar; registre ao menos três ciclos para observar um padrão. Sinais corporais e avaliação profissional são mais informativos.</p></div><div class="area"><div class="eyebrow">Configuração do ciclo</div><label>Primeiro dia da última menstruação (dia 1)</label><input id="ingridLastPeriod" class="field" type="date" value="'+esc(c.lastPeriodStart||"")+'"><label>Duração média do ciclo (adultas: geralmente 21–35 dias)</label><input id="ingridCycleLength" class="field" type="number" min="15" max="60" value="'+esc(c.cycleLength||28)+'"><label>Duração do sangramento (habitualmente até 7 dias)</label><input id="ingridPeriodDays" class="field" type="number" min="1" max="14" value="'+esc(c.periodDays||5)+'"><label>Regularidade do ciclo</label><select id="ingridRegularity" class="field"><option value="unknown" '+(!c.regularity||c.regularity==="unknown"?"selected":"")+'>Ainda não sei</option><option value="regular" '+(c.regularity==="regular"?"selected":"")+'>Regular ou previsível</option><option value="irregular" '+(c.regularity==="irregular"?"selected":"")+'>Irregular ou varia bastante</option></select><label>Data em que você informou que Ingrid percebeu início da janela fértil (observação, não confirmação)</label><input id="ingridObservedFertile" class="field" type="date" value="'+esc(c.observedFertileStart||"")+'"><button id="saveIngridCycle" class="btn gold">Salvar ciclo</button></div><div class="daily-challenge"><div class="eyebrow">Atualização diária de cuidado · '+esc(fmt(today()))+'</div><p><b>'+esc(daily.summary)+'</b></p><p>'+esc(daily.challenge)+'</p><p class="muted">A fase é uma estimativa de calendário; a observação direta e a vontade de Ingrid têm prioridade.</p></div><div class="area"><div class="eyebrow">Observação de hoje</div><label>Como ela parece estar hoje? (observação, não conclusão)</label><select id="ingridSensitivity" class="field"><option value="unknown" '+(!todayCare?.sensitivity||todayCare.sensitivity==="unknown"?"selected":"")+'>Ainda não observei / não sei</option><option value="yes" '+(todayCare?.sensitivity==="yes"?"selected":"")+'>Mais sensível hoje</option><option value="no" '+(todayCare?.sensitivity==="no"?"selected":"")+'>Não notei sensibilidade maior</option></select><label>Quanto espaço para conversa parece desejar?</label><select id="ingridConversation" class="field"><option value="unknown" '+(!todayCare?.conversation||todayCare.conversation==="unknown"?"selected":"")+'>Ainda não sei</option><option value="low" '+(todayCare?.conversation==="low"?"selected":"")+'>Pouca conversa; acolher se ela der espaço</option><option value="open" '+(todayCare?.conversation==="open"?"selected":"")+'>Parece aberta a conversar</option></select><label>Nota de cuidado para hoje</label><textarea id="ingridCareNote" class="field" rows="2" placeholder="Registre somente o que foi observado ou comunicado diretamente...">'+esc(todayCare?.note||"")+'</textarea><button id="saveIngridCare" class="btn gold">Salvar observação de hoje</button></div><div class="area"><div class="eyebrow">Diário compartilhado do matrimônio</div><p class="muted">Registre apenas o que Ingrid consentiu em compartilhar, o que ela pediu e como você respondeu. Evite transformar o diário em vigilância ou avaliação unilateral.</p><label>O que ela compartilhou sobre como está?</label><textarea id="ingridShared" class="field" rows="2" placeholder="Relato dela, com respeito..."></textarea><label>O que ela pediu ou disse que ajudaria?</label><textarea id="ingridRequest" class="field" rows="2" placeholder="Necessidades expressas por ela..."></textarea><label>Qual atitude você tomou?</label><textarea id="ingridResponse" class="field" rows="2" placeholder="Ação concreta de cuidado..."></textarea><button id="saveIngridEntry" class="btn gold">Salvar registro</button></div><div class="area"><div class="row between"><b>Últimos registros</b><button id="lockIngrid" class="btn">Fechar área da Ingrid</button></div>'+(recent||'<p class="muted">Nenhum registro compartilhado ainda.</p>')+'</div><div class="area"><div class="eyebrow">Histórico mensal</div><h3>Ciclos anteriores arquivados</h3>'+archiveHtml+'</div><button id="deleteIngridVault" class="btn danger">Apagar todos os dados de Ingrid deste aparelho</button>';
 $("saveIngridCycle").onclick=async()=>{const last=$("ingridLastPeriod").value,length=Number($("ingridCycleLength").value),days=Number($("ingridPeriodDays").value);if(!last||length<15||length>60||days<1||days>14)return alert("Confira a data e os intervalos informados.");const previous=ingridData.cycle||{};if(previous.lastPeriodStart&&previous.lastPeriodStart!==last){ingridData.archives=ingridData.archives||[];if(!ingridData.archives.some(x=>x.cycle?.lastPeriodStart===previous.lastPeriodStart)){ingridData.archives.push({cycle:{...previous},archivedAt:Date.now()});}}ingridData.cycle={lastPeriodStart:last,cycleLength:length,periodDays:days,regularity:$("ingridRegularity").value,observedFertileStart:$("ingridObservedFertile").value};await saveIngridVault();renderIngrid();};
 $("saveIngridCare").onclick=async()=>{ingridData.dailyCare=ingridData.dailyCare||[];const record={date:today(),sensitivity:$("ingridSensitivity").value,conversation:$("ingridConversation").value,note:$("ingridCareNote").value.trim(),at:Date.now()};const index=ingridData.dailyCare.findIndex(x=>x.date===today());if(index>=0)ingridData.dailyCare[index]=record;else ingridData.dailyCare.push(record);await saveIngridVault();renderIngrid();};
 $("saveIngridEntry").onclick=async()=>{const shared=$("ingridShared").value.trim(),request=$("ingridRequest").value.trim(),response=$("ingridResponse").value.trim();if(!shared&&!request&&!response)return alert("Registre ao menos uma informação.");ingridData.entries=ingridData.entries||[];ingridData.entries.push({date:today(),shared,request,response,at:Date.now()});await saveIngridVault();renderIngrid();};
 $("lockIngrid").onclick=()=>{ingridData=null;ingridUnlocked=false;ingridSessionPin="";ingridVaultSalt=null;renderIngrid();};
 $("deleteIngridVault").onclick=()=>{if(confirm("Apagar definitivamente os dados de Ingrid deste aparelho?")){localStorage.removeItem(INGRID_VAULT_KEY);ingridData=null;ingridUnlocked=false;ingridSessionPin="";ingridVaultSalt=null;renderIngrid();}};
}


const aiSession={token:sessionStorage.getItem("metonoia-session-token")||"",history:[],busy:false,accountDataKey:null};
const aiDimensionRecords={
 "Devoção":["Espiritual"],"Matrimônio":["Matrimonial"],"Profissional":["Profissional"],"Cotidiano":["Cotidiano"]
};
function buildAiContext(){
 const scope=$("aiDimension").value;
 const allowed=aiDimensionRecords[scope];
 let records=db.journal.slice().sort((a,b)=>b.at-a.at);
 if(allowed) records=records.filter(x=>allowed.includes(area(x.dimension)));
 else {
  const picked=[];
  for(const dimension of ["Espiritual","Matrimonial","Profissional","Cotidiano"]){
   picked.push(...records.filter(x=>area(x.dimension)===dimension).slice(0,3));
  }
  records=picked.sort((a,b)=>b.at-a.at);
 }
 records=records.slice(0,12).map(x=>({date:new Date(x.at).toLocaleDateString("pt-BR"),dimension:area(x.dimension),title:x.title||"",content:String(x.body||"").slice(0,1100),checkin:x.checkin||{}}));
 const now=today();
 const agenda=db.items.filter(x=>(!allowed||allowed.includes(area(x.dimension)))&&(!x.date||x.date>=now)).sort((a,b)=>(a.date||"").localeCompare(b.date||"")).slice(0,10).map(x=>({date:x.date||"",time:x.time||"",title:x.title||"",dimension:area(x.dimension),status:x.done?"concluído":"previsto"}));
 return JSON.stringify({registros:records,agenda},null,2).slice(0,12000);
}
function renderAiConversation(){
 const root=$("aiConversation");
 root.innerHTML=aiSession.history.map(m=>'<div class="ai-message '+(m.role==="user"?"ai-user":"ai-assistant")+'"><b>'+(m.role==="user"?"Você":"MEtanoia AI")+'</b><p>'+esc(m.content).replace(/\n/g,"<br>")+'</p></div>').join("")||'<div class="empty">Sua conversa aparecerá aqui.</div>';
 root.scrollTop=root.scrollHeight;
}
async function sendAiQuestion(){
 if(aiSession.busy)return;
 const token=aiSession.token,question=$("aiQuestion").value.trim();
 if(!token){$("aiStatus").textContent="Entre com sua conta MEtanoia antes de conversar com a IA.";return}
 if(!$("aiConsent").checked){$("aiStatus").textContent="Confirme a autorização antes de enviar seus registros.";return}
 if(!question){$("aiStatus").textContent="Escreva uma pergunta antes de enviar.";return}
 aiSession.busy=true;$("aiSend").disabled=true;$("aiStatus").textContent="A inteligência está analisando sua pergunta e o contexto autorizado...";
 const previous=aiSession.history.slice(-8);
 aiSession.history.push({role:"user",content:question});renderAiConversation();$("aiQuestion").value="";
 try{
  const response=await fetch("/api/ai/chat",{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+token},body:JSON.stringify({dimension:$("aiDimension").value,context:buildAiContext(),history:previous,question})});
  const data=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(data.error||"Não foi possível acessar o serviço de IA.");
  aiSession.history.push({role:"assistant",content:data.answer});$("aiStatus").textContent="Resposta gerada pelo modelo conectado. A conversa permanece apenas nesta sessão.";
 }catch(error){
  aiSession.history.pop();$("aiStatus").textContent=error.message||"Falha de conexão. A análise local continua disponível.";
 }finally{
  aiSession.busy=false;$("aiSend").disabled=false;renderAiConversation();
 }
}

const aiOpenButton=$("aiOpen");if(aiOpenButton)aiOpenButton.onclick=()=>{show("intel");$("aiQuestion").focus()};
const accountStatus=(message)=>{const el=$("accountStatus");if(el)el.textContent=message};
function persistentDeviceId(email){
 const storageKey="metonoia-device:"+String(email||"").trim().toLowerCase();
 let id=localStorage.getItem(storageKey);
 if(!id){id=crypto.randomUUID();localStorage.setItem(storageKey,id);}
 return id;
}
const setAccountSession=(data)=>{
 aiSession.token=data.token;sessionStorage.setItem("metonoia-session-token",data.token);
 sessionStorage.setItem("metonoia-user-id",data.userId||"");sessionStorage.setItem("metonoia-device-id",data.deviceId||"");
 $("accountLogout").hidden=false;accountStatus("Conta autenticada neste navegador. A sessão expira em até 30 dias.");
};
async function accountRequest(path,payload,extraHeaders={}){
 const response=await fetch(path,{method:"POST",headers:{"Content-Type":"application/json",...extraHeaders},body:JSON.stringify(payload)});
 const data=await response.json().catch(()=>({}));
 if(!response.ok)throw new Error(data.error||"Não foi possível autenticar a conta.");
 return data;
}
async function unlockAccountCrypto(){
 const phrase=$("accountEncryptionPassphrase").value;
 if(!aiSession.token){accountStatus("Entre na conta antes de inicializar a criptografia.");return}
 if(!window.MEtanoiaAccountCrypto){accountStatus("Módulo de criptografia indisponível. Atualize o aplicativo.");return}
 if(phrase.length<12){accountStatus("Informe uma frase exclusiva com pelo menos 12 caracteres.");return}
 const headers={Authorization:"Bearer "+aiSession.token};
 $("accountUnlock").disabled=true;accountStatus("Verificando chave criptográfica...");
 try{
  let response=await fetch("/api/crypto/key-envelope",{headers});
  let data=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(data.error||"Não foi possível consultar a chave da conta.");
  if(data.envelope){
   aiSession.accountDataKey=await window.MEtanoiaAccountCrypto.openEnvelope(sessionStorage.getItem("metonoia-user-id"),phrase,data.envelope);
  }else{
   const envelope=await window.MEtanoiaAccountCrypto.createEnvelope(sessionStorage.getItem("metonoia-user-id"),phrase);
   response=await fetch("/api/crypto/key-envelope",{method:"POST",headers:{"Content-Type":"application/json",...headers},body:JSON.stringify({envelope})});
   data=await response.json().catch(()=>({}));
   if(!response.ok)throw new Error(data.error||"Não foi possível inicializar a chave da conta.");
   aiSession.accountDataKey=await window.MEtanoiaAccountCrypto.openEnvelope(sessionStorage.getItem("metonoia-user-id"),phrase,envelope);
  }
  $("accountEncryptionPassphrase").value="";
  accountStatus("Criptografia da conta desbloqueada neste navegador. A chave permanece somente na memória desta sessão.");
  try{
   syncEngine=window.MEtanoiaSync;
   syncEngine.configure({
    token:aiSession.token,accountId:sessionStorage.getItem("metonoia-user-id"),
    deviceId:sessionStorage.getItem("metonoia-device-id"),dataKey:aiSession.accountDataKey,
    getDb:()=>db,
    onApply:()=>{localStorage.setItem(K,JSON.stringify(db));renderWord();renderDiary();renderIntel();renderEvolution();renderAgenda();renderHomeReminder()},
    status:accountStatus
   });
   await syncEngine.sync();
  }catch(syncError){accountStatus("Criptografia desbloqueada. Sincronização pendente: "+(syncError.message||"falha de conexão")+". Seus dados locais foram preservados.");}
 }catch(error){
  aiSession.accountDataKey=null;
  accountStatus(error.message||"Não foi possível desbloquear a criptografia.");
 }finally{$("accountUnlock").disabled=false}
}
$("accountUnlock").onclick=unlockAccountCrypto;
$("accountLogin").onclick=async()=>{
 const email=$("accountEmail").value.trim(),password=$("accountPassword").value;
 if(!email||!password){accountStatus("Informe e-mail e senha.");return}
 $("accountLogin").disabled=true;accountStatus("Autenticando...");
 try{setAccountSession(await accountRequest("/api/auth/login",{email,password,platform:"pwa",deviceLabel:"Navegador PWA",deviceId:persistentDeviceId(email)}))}
 catch(error){accountStatus(error.message)}
 finally{$("accountLogin").disabled=false}
};
$("accountRegister").onclick=async()=>{
 const email=$("accountEmail").value.trim(),password=$("accountPassword").value,key=$("registrationKey").value.trim();
 if(!email||password.length<12){accountStatus("Informe e-mail e senha de pelo menos 12 caracteres.");return}
 $("accountRegister").disabled=true;accountStatus("Criando conta...");
 try{setAccountSession(await accountRequest("/api/auth/register",{email,password,platform:"pwa",deviceLabel:"Navegador PWA",deviceId:persistentDeviceId(email)},key?{"X-Registration-Key":key}:{}));$("registrationKey").value=""}
 catch(error){accountStatus(error.message)}
 finally{$("accountRegister").disabled=false}
};
$("accountLogout").onclick=async()=>{
 try{if(aiSession.token)await fetch("/api/auth/logout",{method:"POST",headers:{"Authorization":"Bearer "+aiSession.token}})}catch{}
 syncEngine?.clear();syncEngine=null;aiSession.token="";aiSession.accountDataKey=null;sessionStorage.removeItem("metonoia-session-token");sessionStorage.removeItem("metonoia-user-id");sessionStorage.removeItem("metonoia-device-id");
 $("accountLogout").hidden=true;accountStatus("Sessão encerrada neste navegador.");
};
if(aiSession.token){$("accountLogout").hidden=false;accountStatus("Sessão MEtanoia recuperada nesta aba do navegador.")}
$("aiSend").onclick=sendAiQuestion;
$("aiQuestion").addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();sendAiQuestion()}});
$("aiClear").onclick=()=>{aiSession.history=[];renderAiConversation();$("aiStatus").textContent="Conversa limpa. Os registros locais não foram alterados."};

function show(id){["dashboard","word","areas","agenda","cotidiano","habits","alerts","pastoralStudy","professional","diary","intel","evolution","ingrid"].forEach(n=>$(n).classList.toggle("hidden",n!==id));document.querySelectorAll("[data-tab]").forEach(b=>b.classList.toggle("active",b.dataset.tab===id));if(id==="dashboard")renderDashboard();if(id==="areas")renderWord();if(id==="agenda")renderAgenda();if(id==="ingrid")renderIngrid();if(id==="diary")renderDiary();if(id==="intel")renderIntel();if(id==="evolution")renderEvolution();if(id==="alerts")renderReminderStatus();if(id==="habits")renderHabits();if(id==="professional")renderProfessional()}
document.querySelectorAll("[data-tab]").forEach(b=>b.onclick=()=>show(b.dataset.tab));
document.querySelectorAll("[data-dash-period]").forEach(b=>b.onclick=()=>{dashboardPeriod=Number(b.dataset.dashPeriod);document.querySelectorAll("[data-dash-period]").forEach(x=>x.classList.toggle("active",x===b));renderDashboard()});
$("addCommitment").onclick=()=>modal(null);$("agendaDate").value=agendaDate;$("agendaDate").onchange=()=>{agendaDate=$("agendaDate").value||today();const d=parse(agendaDate);calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);renderAgenda()};const shiftAgenda=amount=>{const d=parse(agendaDate);if(agendaViewMode==="day")d.setDate(d.getDate()+amount);else if(agendaViewMode==="week")d.setDate(d.getDate()+amount*7);else d.setMonth(d.getMonth()+amount);agendaDate=key(d);calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);renderAgenda()};$("prevMonth").onclick=()=>shiftAgenda(-1);$("nextMonth").onclick=()=>shiftAgenda(1);setInterval(()=>{checkReminderClock();renderHomeReminder();const now=today();if(now!==observedToday){if(agendaDate===observedToday){agendaDate=now;calendarMonth=new Date(parse(now).getFullYear(),parse(now).getMonth(),1);renderAgenda()}observedToday=now;renderWord();renderDailyJourney();$("todayDate").textContent=new Date().toLocaleDateString("pt-BR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"})}},15000);document.addEventListener("visibilitychange",()=>{if(!document.hidden)checkReminderClock()});
document.body.addEventListener("click",e=>{const dashArea=e.target.closest("[data-dash-area]");if(dashArea){show("diary");$("jd").value=dashArea.dataset.dashArea;renderJournalPrompts();return;}const areaAction=e.target.closest("[data-open-section]");if(areaAction){const target=areaAction.dataset.openSection;show(target);if(target==="diary"){$("jd").value=areaAction.dataset.dimension;renderJournalPrompts();}return;}const agendaView=e.target.closest("[data-agenda-view]");if(agendaView){agendaViewMode=agendaView.dataset.agendaView;renderAgenda();return;}const calendarDay=e.target.closest("[data-calendar-date]");if(calendarDay){agendaDate=calendarDay.dataset.calendarDate;const d=parse(agendaDate);calendarMonth=new Date(d.getFullYear(),d.getMonth(),1);renderAgenda();return}const diary=e.target.closest("[data-open-diary]");if(diary){show("diary")}const ans=e.target.closest("[data-answer]"),ed=e.target.closest("[data-edit]"),del=e.target.closest("[data-del]");if(ans){const x=db.items.find(z=>z.id===ans.dataset.answer);if(x)answer(x,ans.dataset.date)}else if(ed){const x=db.items.find(z=>z.id===ed.dataset.edit);if(x)modal(x)}else if(del){const id=del.dataset.del;if(confirm("Excluir este compromisso?")){const deleted=db.items.find(x=>x.id===id);syncEngine?.markDeleted("items",id,deleted?.dimension||"Cotidiano");db.items=db.items.filter(x=>x.id!==id);save();renderAgenda();renderEvolution();renderHabits();renderDashboard()}}});
function confessionAdvice(raw){
 const t=normalizeText(raw);
 if(/irmao|irma|ofendi|ofensa|mago|feri|machuquei|injusti/.test(t))return {title:"Quando você pecou contra alguém",ref:"Mateus 5:23–24; Mateus 18:15; Efésios 4:25–32",text:"Jesus ensina que a reconciliação com o irmão não deve ser tratada como detalhe. Efésios chama a abandonar a mentira, a amargura e palavras que destroem, buscando palavras que edifiquem. O texto não manda esconder o dano: chama à verdade e à responsabilidade.",step:"Se você prejudicou alguém, reconheça com clareza o que fez, sem justificar nem transferir a culpa. Procure seu irmão em particular quando for seguro e apropriado, peça perdão e pergunte como pode reparar o dano. Se a situação for complexa, procure seu pastor para acompanhamento."};
 if(/menti|mentira|engan|omiti|fals/.test(t))return {title:"Quando houve mentira ou engano",ref:"Efésios 4:25; Provérbios 12:22; 1 João 1:7–9",text:"A Escritura chama a abandonar a falsidade e a falar a verdade. Confissão bíblica não é apenas sentir culpa: é trazer à luz, assumir responsabilidade e voltar a andar na verdade.",step:"Corrija a informação falsa com quem foi afetado, não crie novas justificativas e procure reparar as consequências. Converse com seu pastor se precisar de ajuda para fazer isso com sabedoria."};
 if(/ira|raiva|gritei|grito|explodi|agredi|ameac/.test(t))return {title:"Quando a ira dominou sua atitude",ref:"Tiago 1:19–20; Efésios 4:26–32; Provérbios 15:1",text:"A Palavra orienta a ser pronto para ouvir e tardio para falar e irar-se. A ira humana não produz a justiça de Deus. Reconhecer o erro exige interromper a conduta que causa dano e buscar mudança concreta.",step:"Não minimize o que aconteceu. Se alguém está em risco, afaste-se da situação e busque ajuda imediatamente. Depois, assuma sua atitude, procure reparar o dano e converse com seu pastor e um profissional qualificado quando necessário."};
 if(/sexual|porn|adult[eé]rio|trai|impure/.test(t))return {title:"Quando houve quebra de fidelidade ou pureza",ref:"Mateus 5:27–30; 1 Coríntios 6:18–20; 1 João 1:9",text:"A Escritura chama à santidade, à fidelidade e à verdade. A graça não encobre o pecado nem autoriza sua continuidade; ela chama ao arrependimento e a passos concretos de mudança.",step:"Interrompa o comportamento, não manipule nem culpe terceiros, considere quem foi afetado e busque acompanhamento pastoral maduro. Se houver risco à saúde ou segurança, procure também atendimento profissional."};
 if(/roubei|furtei|peguei|dinheiro|desviei|fraude/.test(t))return {title:"Quando houve prejuízo material ou desonestidade",ref:"Efésios 4:28; Lucas 19:8–9; Provérbios 11:1",text:"A Bíblia chama quem furtava a deixar essa prática, trabalhar honestamente e repartir. A transformação inclui restituição responsável quando houve prejuízo.",step:"Pare a prática, apure o dano real e procure orientação pastoral e, quando necessário, jurídica ou financeira para reparar de modo seguro e correto."};
 return {title:"Confissão, arrependimento e restauração",ref:"1 João 1:8–9; Provérbios 28:13; Salmo 51:1–12",text:"A Escritura não ensina a esconder nem a justificar o pecado. Ela chama a confessar, abandonar o caminho errado e buscar a misericórdia de Deus. O arrependimento bíblico envolve mudança de direção, não apenas remorso.",step:"Diga a verdade diante de Deus, identifique quem foi afetado, interrompa o comportamento e pense em uma reparação concreta. Quando envolver outra pessoa, procure-a com humildade se isso for seguro; busque seu pastor para caminhar com responsabilidade."};
}
function runConfession(){
 const raw=$("confessionText").value.trim();if(!raw)return alert("Escreva com sinceridade o que deseja confessar.");
 const a=confessionAdvice(raw);
 $("confessionResult").innerHTML='<article class="reading-page"><div class="eyebrow">Orientação bíblica temporária</div><h3>'+esc(a.title)+'</h3><div class="goldtext">'+esc(a.ref)+'</div><p>'+esc(a.text)+'</p><p><b>Próximo passo:</b> '+esc(a.step)+'</p><p class="reading-note">Leia os textos no contexto e não use esta orientação para se condenar ou para encobrir consequências. A análise é local e baseada em padrões de palavras; ela pode não compreender nuances do relato. Para uma situação séria ou complexa, procure seu pastor e apoio profissional adequado.</p><button class="btn gold" id="finishConfession">Finalizar e apagar este relato</button></article>';
 $("confessionResult").scrollIntoView({behavior:"smooth",block:"start"});
 $("finishConfession").onclick=()=>{$("confessionText").value="";$("confessionResult").innerHTML='<div class="empty">Relato temporário encerrado. O texto digitado não foi salvo no diário nem no histórico do aplicativo.</div>';};
}
$("jd").onchange=renderJournalPrompts;$("confessionRun").onclick=runConfession;$("confessionClear").onclick=()=>{$("confessionText").value="";$("confessionResult").innerHTML="";};$("saveJ").onclick=()=>{const body=$("jb").value.trim(),dimension=$("jd").value,checkin={};document.querySelectorAll("[data-jprompt]").forEach(el=>{if(el.value.trim())checkin[el.dataset.jprompt]=el.value.trim()});if(!body&&!Object.keys(checkin).length)return alert("Registre ao menos uma informação.");db.journal.push({id:uid(),title:$("jt").value.trim(),body,dimension,checkin,at:Date.now()});save();$("jt").value="";$("jb").value="";renderDiary();renderIntel();renderEvolution();renderDashboard();alert("Registro salvo neste aparelho. A sincronização criptografada ocorrerá quando a conta estiver autenticada e a criptografia desbloqueada.")};
$("saveR").onclick=()=>{const body=$("rb").value.trim();if(!body)return alert("Escreva sua revisão.");db.reviews.push({id:uid(),body,at:Date.now()});save();$("rb").value="";renderEvolution()};$("enableReminders").onclick=enableReminders;
window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();promptInstall=e;$("install").hidden=false});$("install").onclick=async()=>{if(promptInstall){promptInstall.prompt();await promptInstall.userChoice;promptInstall=null;$("install").hidden=true}};
if("serviceWorker"in navigator)addEventListener("load",async()=>{try{const registration=await navigator.serviceWorker.register("/sw.js",{updateViaCache:"none"});await registration.update();}catch(error){console.warn("Não foi possível verificar atualização do MEtanoia",error);}});
$("todayDate").textContent=new Date().toLocaleDateString("pt-BR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});renderDashboard();renderDailyJourney();renderWord();renderDiary();renderIntel();renderEvolution();renderAgenda();renderHomeReminder();show("dashboard");
})();