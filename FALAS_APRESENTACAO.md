# Sync Monitor — Falas da apresentação (Mayumi, Daniel, Vinicius, Rodrigo, Fabricia, Heloiza e Vitor)

**Como usar este documento:** cada integrante tem uma fala pronta de 2 a 3 minutos, escrita para quem nunca programou entender. Os trechos em destaque azul (👉) são as **ações na tela** e não precisam ser lidos em voz alta. Leia em voz alta só o texto normal. Não precisa decorar: entenda a ideia e fale com as suas palavras.

> 👉 Antes de começar: app aberto no **menu inicial**, emulador visível para a turma e o log limpo (tela **Log de atividades → Limpar**).

---

## 1. Mayumi — Abertura e o problema da bateria
**Tela:** menu inicial → 🔋 Bateria e Doze Mode • **Tempo:** ~3 min

Boa noite, pessoal! Nós vamos apresentar o **Sync Monitor**, um aplicativo que a gente criou para mostrar, na prática, como um app faz coisas "escondido", em segundo plano, sem acabar com a bateria do celular.

> 👉 Mostre o menu inicial.

Esta é a tela inicial. Cada cartão aqui é um assunto diferente, e cada um de nós vai apresentar um deles. Eu começo pelo problema que deu origem a tudo isso: **a bateria**.

Pensa no seu celular agora. Tem WhatsApp, Instagram, e-mail, jogo… todos querendo fazer alguma coisa ao mesmo tempo. Se o Android deixasse cada app fazer o que quisesse escondido, a bateria acabaria antes do almoço. Por isso o sistema funciona como um **porteiro rigoroso**: ele controla quem pode trabalhar quando a tela está desligada.

> 👉 Toque em **🔋 Bateria e Doze Mode**.

Aqui o app lê informações reais do aparelho. Esse "**Doze**" é como o celular **tirando um cochilo**: quando ele está parado na mesa com a tela apagada, o Android segura quase tudo para economizar energia. E esse "**App Standby Bucket**" funciona como uma **fila de prioridade**: o app que você usa toda hora fica na frente, e o app que você esqueceu vai para o fim da fila e ganha menos permissão para trabalhar.

> 👉 Role até **"Qual ferramenta usar?"**.

Então, quando um programador precisa que o app faça algo em segundo plano, ele tem três caminhos. O **WorkManager** é como deixar um bilhete: "faz isso quando der". O **Bound Service** é uma conversa que só dura enquanto a tela está aberta. E o **Foreground Service**, que é o nosso tema, é para quando a tarefa é importante e contínua, como um GPS de corrida ou uma música tocando. A regra dele é simples: **pode trabalhar, mas tem que avisar o usuário**, mostrando uma notificação fixa.

E é exatamente sobre esse aviso que a próxima pessoa vai falar.

---

## 2. Daniel — Permissões e notificações
**Tela:** 🔔 Notificações e permissões • **Tempo:** ~2 min 30 s

Obrigado(a)! Como foi dito, o app precisa **avisar** o usuário que está trabalhando. Mas, antes de avisar, ele precisa **pedir licença**.

> 👉 Volte ao menu e toque em **🔔 Notificações e permissões**.

Sabe quando você instala um app e aparece aquela janelinha: "Permitir que este app envie notificações?" É isso aqui. Desde o Android 13, o app **não pode** mandar notificação sem você dizer "sim". É como uma **campainha**: o app só pode tocar se você deixar.

> 👉 Mostre o cartão **"Situação da permissão"**.

Aqui o app mostra que a permissão foi concedida. Se a pessoa negar, o app continua funcionando, mas o aviso some. Ninguém fica sabendo que ele está trabalhando, e é por isso que essa permissão é tão importante.

> 👉 Mostre o cartão **"Canais de notificação"**.

Outra coisa interessante são os **canais**. Pense numa **caixa de correio com divisórias**: uma divisória para avisos silenciosos e outra para alertas com som. O nosso app tem duas: uma silenciosa, que mostra o progresso do trabalho e não incomoda, e outra para alertas importantes, como bateria baixa. A vantagem é que o usuário pode silenciar uma sem perder a outra.

> 👉 Toque em **🔔 Enviar notificação de teste** e abra a gaveta de notificações.

Olha ela aqui, chegando pelo canal de alertas. Tudo isso fica registrado no arquivo de configuração do app, o "manifesto", que é como a **certidão** do aplicativo: diz quem ele é e o que pode fazer.

Agora que o app tem permissão para avisar, vamos ver o trabalhador de verdade.

---

## 3. Vinicius — O serviço e o seu ciclo de vida
**Telas:** ⚙️ Foreground Service → 📜 Log de atividades • **Tempo:** ~3 min

Agora vem o coração do projeto: o **serviço**.

> 👉 Volte ao menu e toque em **⚙️ Foreground Service**.

Um serviço é uma parte do app **que trabalha sem tela**. Imagine um **funcionário nos bastidores de um teatro**: o público não vê, mas ele está lá, trocando cenário e ajustando a luz. O nosso funcionário faz uma "sincronização": coleta informações e manda para um servidor, em ciclos, um atrás do outro.

> 👉 Toque em **▶ Iniciar Serviço**.

Pronto, ele começou! Vejam que o status mudou para "Executando", o cronômetro está contando e a tarefa atual vai mudando sozinha.

Todo serviço tem um **ciclo de vida**, como um expediente de trabalho. Ele **chega** (o `onCreate`), **recebe as ordens** (o `onStartCommand`) e, quando termina, **vai embora e apaga a luz** (o `onDestroy`). E tem uma regra curiosa: assim que começa, ele tem **apenas 5 segundos** para mostrar a notificação. Se não mostrar, o Android encerra o app. É como um crachá: entrou, tem que mostrar.

> 👉 Toque em **📜 Ver log completo →** e no filtro **"Ciclo de vida"**.

Esse é o **diário** do nosso funcionário. Cada linha mostra o que aconteceu e a que horas. Aqui dá para ver a ordem certinha: criado, recebeu a ordem, começou a trabalhar.

> 👉 Volte, toque em **Parar Serviço** e mostre o `onDestroy()` no log.

E aqui o fim do expediente: o serviço foi encerrado e **liberou tudo o que estava usando**. Isso é importante para não desperdiçar memória nem bateria.

Mas tem uma pegadinha nessa história, e é sobre ela que a próxima pessoa vai falar.

---

## 4. Rodrigo — A armadilha da Main Thread
**Tela:** 🧵 Main Thread vs Corrotinas • **Tempo:** ~3 min

A pegadinha é a seguinte: muita gente acha que o serviço trabalha "em outro lugar", separado da tela. **Não trabalha.** Por padrão, ele usa a mesma "mão" que desenha a tela.

> 👉 Volte ao menu e toque em **🧵 Main Thread vs Corrotinas**.

Imaginem um **restaurante com um único garçom**. Ele anota pedidos, leva pratos e atende as mesas. Esse garçom é a **Main Thread**, a linha principal do app. Agora, se você manda esse garçom lavar toda a louça da cozinha, o que acontece? **Ninguém mais é atendido.** O restaurante para.

> 👉 Mostre o contador e o círculo girando.

Esse contador e esse círculo são o nosso "garçom atendendo as mesas". Enquanto eles se mexem, o app está respondendo.

> 👉 Toque em **❌ Executar na Main Thread**.

Vejam: **congelou tudo!** Por três segundos o app ficou travado, porque mandamos o garçom lavar a louça. Se passar de cinco segundos, o Android mostra aquela mensagem chata: "O app não está respondendo. Deseja fechar?"

> 👉 Toque em **✅ Executar com Corrotina em Dispatchers.IO**.

Agora o mesmo trabalho de três segundos, mas feito do jeito certo. Reparem que o contador **continua andando**. O que fizemos foi **contratar um ajudante de cozinha**. Isso é a **Corrotina**: ela manda o trabalho pesado para outra equipe, o "Dispatchers.IO", e o garçom continua livre para atender.

> 👉 Mostre o nome da thread na mensagem: `DefaultDispatcher-worker`.

Esse nome aqui é o crachá do ajudante, que comprova que o trabalho foi feito em outro lugar. Nosso serviço de sincronização usa exatamente essa técnica, por isso ele nunca trava o app.

---

## 5. Fabricia — A notificação que fala com o usuário
**Telas:** ⚙️ Foreground Service + gaveta de notificações • **Tempo:** ~2 min 30 s

Até agora vimos o funcionário trabalhando. Agora quero mostrar como ele **presta contas** para o usuário, mesmo com o app fechado.

> 👉 Abra **⚙️ Foreground Service**, toque em **▶ Iniciar Serviço** e depois aperte o botão **Home** do emulador.

Saímos do app. Ele sumiu da tela, mas continua trabalhando. Como eu sei disso?

> 👉 Puxe a gaveta de notificações e expanda a notificação.

Por causa desta notificação. Ela é como o **painel de uma entrega por aplicativo**, aquele que mostra "seu pedido saiu, está a 5 minutos". Aqui temos:
- uma **barra de progresso** mostrando em que etapa o trabalho está;
- um **cronômetro** dizendo há quanto tempo ele roda;
- e, quando expandimos, os **detalhes**: bateria, rede, quantos pacotes já foram enviados.

E ela é **fixa**: não dá para arrastar e apagar enquanto o serviço está ativo. Isso é de propósito, por transparência.

> 👉 Toque em **Pausar** na notificação, espere um pouco e toque em **Retomar**.

Ela também tem **botões**. Dá para pausar e retomar o trabalho direto daqui, sem abrir o app. É como um **controle remoto**.

> 👉 Toque na notificação para abrir o app e depois toque em **Parar** (na notificação ou na tela).

Tocando nela, voltamos direto para a tela do serviço. E, quando paramos, ele deixa um **resumo** da sessão: quantos ciclos fez, quanto enviou e quantas falhas teve. É o "relatório do fim do turno".

---

## 6. Heloiza — O que acontece nos bastidores
**Telas:** ⚙️ Foreground Service → 📜 Log de atividades • **Tempo:** ~3 min

Eu vou mostrar **o que esse funcionário faz em cada ciclo** e o que acontece quando algo dá errado.

> 👉 Abra **⚙️ Foreground Service** e inicie o serviço.

Cada ciclo tem **cinco etapas**, como uma **linha de montagem**:
1. Primeiro ele confere a **bateria**, e essa leitura é real, do aparelho.
2. Depois verifica a **internet**: se é Wi-Fi, dados móveis ou se está sem sinal.
3. Aí **coleta os dados**, como se fossem os passos de um app de corrida.
4. Em seguida **compacta** esses dados, como quem tira o ar de uma mala para caber mais roupa. Vejam aqui: o pacote fica cerca de **90% menor**, o que economiza internet.
5. E, por fim, **envia** para o servidor.

> 👉 Mostre o cartão de métricas e mude o **intervalo** no slider.

Dá até para ajustar o ritmo: de quanto em quanto tempo ele trabalha. Mais rápido gasta mais bateria; mais devagar economiza.

> 👉 Ligue **"Simular falha de rede"** e abra o **Log de atividades** com o filtro **"Avisos e erros"**.

E se a internet cair? Ligando esta chave, simulamos uma falha. Reparem que o app não fica insistindo sem parar, o que gastaria bateria à toa. Ele **espera 2 segundos, depois 4, depois 8**: cada vez espera mais. Isso se chama **backoff exponencial**. É como ligar para alguém que não atende: você não liga 50 vezes seguidas, você dá um tempo maior a cada tentativa.

> 👉 Desligue a simulação e toque em **Exportar**.

E todo esse diário pode ser exportado e compartilhado, o que é ótimo para descobrir problemas.

---

## 7. Vitor — Desafio 1: o sensor e o encerramento
**Tela:** 📱 Desafio 1 — Acelerômetro (+ Profiler) • **Tempo:** ~3 min

Para fechar, eu vou mostrar outro conceito importante: **como o app escuta o que acontece no mundo real** sem desperdiçar energia.

> 👉 Volte ao menu e toque em **📱 Desafio 1 — Acelerômetro**.

Todo celular tem um **acelerômetro**, o sensor que percebe quando você gira o aparelho, por exemplo para a foto ficar em pé. Esses três números, X, Y e Z, são os três eixos. Esse 9,8 aqui no Y é a **gravidade da Terra** puxando o celular para baixo!

> 👉 Gire o aparelho em **⋯ → Virtual sensors** e mostre os valores mudando.

Agora a parte importante: **como** o app recebe esses números. Existem dois jeitos. O jeito ruim é o app ficar perguntando o tempo todo: "mudou? mudou? mudou?", igual criança no banco de trás: "já chegou?". Isso gasta bateria à toa. O jeito certo, que usamos, é o padrão **Observer**: o app se **inscreve** e o sensor **avisa quando muda**. É como **se inscrever num canal e ativar o sininho**: você não fica atualizando a página, o aviso chega sozinho.

E tem um detalhe: o app **só se inscreve enquanto a tela está aberta** e **cancela a inscrição quando você sai**. É como **apagar a luz ao sair do quarto**.

> 👉 Abra o **Profiler** no Android Studio, mostre a CPU com **FASTEST** e aperte **Home**.

Vejam o gráfico do processador: com a tela aberta, ele está trabalhando. Quando saio do app… **cai para quase zero**. A luz foi apagada.

> 👉 Ligue **"Simular erro: NÃO cancelar no onPause()"** e repita.

Agora simulei o erro de **esquecer a luz acesa**. Saio do app e o processador **continua trabalhando**, gastando bateria para nada. Um detalhe pequeno no código faz toda a diferença para quem usa o celular.

> 👉 Volte ao menu inicial.

Então, resumindo tudo o que vimos hoje: o Android **protege a bateria**; um app que trabalha escondido **precisa avisar** o usuário; o trabalho pesado vai para um **ajudante**, as corrotinas; quando algo falha, o app **tenta de novo com calma**; e, quando não precisa mais escutar, **apaga a luz**. Isso é **código de alta performance**: um app que funciona bem e respeita o celular de quem usa.

Muito obrigado! Estamos abertos para perguntas.

---

## Respostas rápidas para perguntas da turma
- **"Por que o app precisa mostrar notificação?"** Para o usuário saber que algo está gastando bateria. É transparência.
- **"E se eu negar a permissão?"** O app continua funcionando, mas você não vê o aviso.
- **"O que é uma thread?"** É uma "linha de trabalho" do app, como um funcionário. A principal (Main Thread) cuida da tela.
- **"O que é corrotina?"** Um jeito leve de mandar trabalho para outro "funcionário" sem travar a tela.
- **"Isso existe em apps reais?"** Sim: Spotify tocando música, Strava gravando corrida e Google Fotos fazendo backup usam essas mesmas técnicas.
