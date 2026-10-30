# Isoforge / Isocraft Forge (Minecraft 1.20.1) — camera isometrica + ARPG controls

Mod **somente de cliente** para Forge 47.3.0. Esta linha de desenvolvimento reproduz, por implementacao Forge, recursos observaveis na referencia Isocraft Fabric 1.21.11; nao incorpora classes Fabric. Compativel com **Embeddium/Rubidium** e **Oculus**, com adaptacoes especificas para 1.20.1.

## Como compilar (Java 17)

Os arquivos do Gradle Wrapper estao incluidos. A primeira compilacao precisa de internet para baixar o Gradle 8.8 e as dependencias do Forge. O jeito mais facil:

1. Baixe o **Forge MDK 1.20.1-47.3.0** em https://files.minecraftforge.net/ (botao "Mdk") e extraia.
2. Copie para dentro da pasta do MDK, **substituindo** os arquivos de mesmo nome:
   `build.gradle`, `settings.gradle`, `gradle.properties` e a pasta `src/` (apague o `src/` de exemplo antes).
3. Rode `./gradlew build` (Windows: `gradlew.bat build`). Na primeira vez baixa tudo, pode demorar.
4. O mod sai em `build/libs/isoforge-forge-1.20.1-0.2.0.jar`. Coloque em `.minecraft/mods`.

Para testar sem gerar jar: `./gradlew runClient`.

Requisitos no jogo: Forge 47.x (1.20.1). Opcional: Embeddium 0.3.x ou Rubidium, e Oculus.

## Controles

| Tecla | Acao |
|---|---|
| **F7** | liga/desliga o modo isometrico |
| **Ctrl + scroll** | zoom |
| **F8** | liga/desliga o dollhouse reveal |
| **F6** | abre configuracoes do Isocraft durante o jogo |
| **Z / X (segurar)** | gira a camera continuamente |
| **Page Up / Page Down** | ajusta a inclinacao da camera |
| **= / -** | aproxima / afasta o zoom |
| **Ctrl + clique direito** | define um destino para o click-to-move |
| **WASD** | anda relativo a camera (qualquer direcao pode correr) |
| **Mouse** | o jogador olha para o cursor; clicar bate/minera/coloca no ponto do cursor |

Todas as teclas e opcoes (angulo, zoom, raios do reveal etc.) podem ser mudadas em Controles e em
`config/isoforge-client.toml`.

## Como o reveal funciona nesta versao Forge

O reveal acontece **na geracao da malha**: os mixins fazem `getBlockState` devolver ar nos blocos escondidos,
entao a geometria nao e criada. Isso permite que o corte funcione junto do pipeline normal de renderizacao e
com Oculus, embora shaders que dependem de uma projecao exata ainda possam apresentar artefatos.

- **Comodos:** uma busca limitada acompanha o espaco vazio conectado ao jogador. Quando encontra cobertura em
  pelo menos quatro colunas desse espaco, corta cada coluna a partir do proprio teto, preservando melhor paredes
  e andares vizinhos do que um corte horizontal unico.
- **Fallback em areas abertas:** sem um comodo coberto detectavel, o corte de teto continua ativo: blocos a
  partir do primeiro teto acima do jogador somem dentro do raio configurado.
- **Tubo de visao:** um funil entre o jogador e a camera esconde geometria que bloqueia a linha de visao, incluindo a base de paredes na altura dos pes. O piso abaixo do jogador permanece preservado.
- Histerese (`debounceTicks`), ancoragem (`anchorStep`) e limite de reconstrucoes por tick (`rebuildsPerTick`)
  reduzem oscilacao e picos de trabalho.

Este e um port Forge parcial do comportamento observavel da Fabric, nao uma copia integral do pipeline de
oclusao dela. A Fabric possui mais politicas de topologia, transicoes e composicao de mascara que ainda nao
foram reimplementadas aqui.

A busca pelo comodo e limitada por `ceilingRadius` e `ceilingScan`, atualizada quando a ancora se move e,
no maximo, a cada 10 ticks. Para limitar o custo em espacos muito abertos, ela interrompe a analise acima de
65.536 celulas. Leituras incompletas (por limite ou area ainda nao carregada) nao publicam mascaras parciais:
uma falha isolada preserva o ultimo corte por alguns instantes; falhas consecutivas descartam a mascara antiga.
Ao tocar em um chunk nao carregado durante a busca, a leitura inteira e marcada como incompleta para nao
recortar uma sala pela metade. Os ajustes de inclinacao por tecla tambem usam interpolacao entre ticks.

## IMPORTANTE para Embeddium / Rubidium

Desative **"Use Occlusion Culling"** nas opcoes de video (aba Advanced). A camera fica ~64 blocos longe do
jogador, possivelmente dentro de terreno, e o culling por oclusao pode esconder chunks errados.

## Camera sem ortografica de verdade

Em vez de projecao ortografica, usa **perspectiva com FOV muito estreito** e a camera bem longe. Visualmente
quase igual, mas mantem a matematica de projecao que os shaderpacks esperam (profundidade, sombras, SSAO).
Aumente `distance` no config para chegar mais perto do ortografico.

## Limitacoes conhecidas (v0.2.0)

- O detector de comodos e uma busca local simplificada; ainda nao reproduz a classificacao de salas/cavernas,
  handoffs de camera, mascaras espaciais e politicas topologicas completas da Fabric.
- O HUD da Forge e intencionalmente leve nesta primeira etapa: estado da camera, alvo, vida e fome. Ainda nao inclui os orbes texturizados, numeros de dano, nameplates, loot beams, menus-bolha ou fotos/GIFs da Fabric.
- Click-to-move usa A* local com limite de expansao e degraus de um bloco; nao e ainda o pathfinder completo da Fabric e nao atravessa fluidos ou parkour.
- A tela de configuracao ja altera opcoes em tempo real; ainda faltam abas, presets visuais e todos os controles avancados da Fabric.
- Embeddium: o mixin alveja `me.jellysquid.mods.sodium.client.world.WorldSlice` (Embeddium 0.3.x / Rubidium).
  Se nao aplicar na sua versao, o log mostra e o reveal so funciona no renderizador vanilla.
- Shaderpacks que dependem de matriz de projecao exata podem ter pequenos artefatos.

## Escopo do port 0.2.0

Esta entrega e uma etapa funcional de portabilidade, nao uma paridade completa. A base Forge agora tem tela de
configuracao, HUD leve, click-to-move local, controles extras de camera e detector local de comodos. Sistemas
complexos da referencia — incluindo o pipeline de visibilidade de cavernas, transicoes da camera, HUDs
texturizados, menus-bolha, gravacao de GIF e compatibilidade profunda com Sodium — continuam pendentes.

## Recursos adicionados na linha 0.2.0

- Painel de configuracao em jogo (F6), com persistencia no TOML do cliente.
- HUD ARPG com leitura de vida/fome, estado do reveal, zoom, yaw e indicador de mira.
- Click-to-move opcional com Ctrl + clique direito e busca A* limitada a blocos caminhaveis.
- Click-to-move revalida o proximo passo e tenta recalcular a rota quando o caminho muda (por exemplo, uma porta ou bloco bloqueando a passagem).
- HUD mostra o destino e a quantidade de passos restantes durante o click-to-move; ao mirar em uma entidade viva, exibe nome, vida e distancia.
- A busca do teto do reveal interrompe a leitura ao encontrar um chunk nao carregado, evitando consultar terreno fora da area disponivel ao cliente.
- Rotacao continua por tecla pressionada, ajustes de pitch e teclas dedicadas de zoom.

## Se algo der errado

Mande o **log do build** (erros de compilacao) ou o `logs/latest.log` (erros de mixin / crash) para ajuste.
