<div align="center">

<img src="./frontend/src/main/resources/com/sicad/assets/logo.png" width="35%" alt="Logo do SICAD">

<br><br>

<p><strong>Sistema Inteligente de Cadastro e Administração de Dispositivos.</strong></p>

<p>
Uma plataforma para gerenciamento de clientes, dispositivos e comunicação em tempo real, oferecendo controle centralizado e monitoramento de conexões.
</p>

</div>

---

# Sumário

<ul id="nav">
  <li><a href="#problema">1. O Problema</a></li>
  <li><a href="#solucao">2. A Solução</a></li>
  <li><a href="#funcionalidades">3. Principais Funcionalidades</a></li>
  <li><a href="#publico">4. Público-alvo</a></li>
  <li><a href="#tecnologias">5. Tecnologias Utilizadas</a></li>
  <li><a href="#arquitetura">6. Arquitetura</a></li>
  <li><a href="#links">7. Links Úteis</a></li>
  <li><a href="#equipe">8. Equipe</a></li>
</ul>

---

<h2 id="problema">1. O Problema :warning:</h2>

<p>
Empresas que realizam atendimento, suporte técnico ou gerenciamento de equipamentos frequentemente enfrentam dificuldades para manter um controle centralizado de seus clientes e dispositivos.
</p>

<p>
Informações distribuídas em diferentes sistemas ou planilhas dificultam o acompanhamento dos equipamentos cadastrados, aumentando o tempo gasto em operações e tornando o processo suscetível a erros.
</p>

<p>
Além disso, a comunicação entre aplicações e dispositivos geralmente exige mecanismos de conexão confiáveis e em tempo real, permitindo o envio e recebimento de informações de forma contínua.
</p>

---

<h2 id="solucao">2. A Solução :sparkles:</h2>

<p>
O <strong>SICAD</strong> foi desenvolvido para centralizar o gerenciamento de clientes e dispositivos em uma única plataforma.
</p>

<p>
O sistema possui uma arquitetura composta por uma aplicação Desktop desenvolvida em JavaFX, um servidor Java responsável pelo gerenciamento das conexões TCP, banco de dados PostgreSQL e infraestrutura baseada em Docker.
</p>

<p>
A comunicação entre cliente e servidor ocorre em tempo real através de sockets TCP, permitindo o gerenciamento e sincronização das informações de forma eficiente.
</p>

---

<h2 id="funcionalidades">3. Principais Funcionalidades :gear:</h2>

<ul>
  <li>Cadastro de clientes;</li>
  <li>Gerenciamento de dispositivos;</li>
  <li>Comunicação em tempo real via TCP;</li>
  <li>Autenticação de usuários;</li>
  <li>Persistência de dados em PostgreSQL;</li>
  <li>Migrações automáticas utilizando Flyway;</li>
  <li>Servidor multiusuário baseado em sockets;</li>
  <li>Infraestrutura conteinerizada com Docker;</li>
  <li>Balanceamento de carga utilizando Nginx;</li>
  <li>Publicação segura através do Cloudflare Tunnel.</li>
</ul>

---

<h2 id="publico">4. Público-alvo :dart:</h2>

<p>
O SICAD foi desenvolvido para empresas e organizações que necessitam controlar clientes, dispositivos e conexões de forma centralizada.
</p>

<p>
Sua arquitetura permite adaptação para diferentes cenários que envolvem monitoramento, comunicação em tempo real e gerenciamento de equipamentos.
</p>

<p>
Entre os potenciais usuários estão:
</p>

<ul>
  <li>Empresas de tecnologia;</li>
  <li>Empresas de monitoramento;</li>
  <li>Prestadores de suporte técnico;</li>
  <li>Centrais de atendimento;</li>
  <li>Empresas de automação;</li>
  <li>Instituições públicas e privadas.</li>
</ul>

---

<h2 id="tecnologias">5. Tecnologias Utilizadas :computer:</h2>

<ul>
  <li>Java 24;</li>
  <li>JavaFX;</li>
  <li>Maven;</li>
  <li>PostgreSQL;</li>
  <li>Flyway;</li>
  <li>Docker;</li>
  <li>Docker Compose;</li>
  <li>Nginx;</li>
  <li>Cloudflare Tunnel;</li>
  <li>Sockets TCP.</li>
</ul>

---

<h2 id="arquitetura">6. Arquitetura :building_construction:</h2>

```text
                 +-------------------+
                 |   JavaFX Client   |
                 +---------+---------+
                           |
                      TCP Socket
                           |
                 +---------v---------+
                 |   Java Backend    |
                 +---------+---------+
                           |
                     PostgreSQL
                           |
                 +---------v---------+
                 |      Docker       |
                 +---------+---------+
                           |
                 Cloudflare Tunnel
                           |
                       Internet
```

---

<h2 id="links">7. Links Úteis :link:</h2>

<p>
Em breve.
</p>

---

<h2 id="equipe">8. Equipe :busts_in_silhouette:</h2>

<table align="center">
<tr>

<td align="center" width="220px">

<img src="https://avatars.githubusercontent.com/u/90107368?v=4" width="100" height="100">

<br><br>

<strong>Luis Gustavo Alves Correia</strong>

<br>

<sub>Desenvolvedor</sub>

</td>

<td align="center" width="220px">

<img src="https://avatars.githubusercontent.com/u/78867396?v=4" width="100" height="100">

<br><br>

<strong>Felipe Silva</strong>

<br>

<sub>Desenvolvedor</sub>

</td>

<td align="center" width="220px">

<img src="https://avatars.githubusercontent.com/u/124806745?v=4" width="100" height="100">

<br><br>

<strong>Luiz Henrique</strong>

<br>

<sub>Desenvolvedor</sub>

</td>

</tr>
</table>

---

<div align="center">

Desenvolvido com ❤️ utilizando Java, JavaFX, PostgreSQL e Docker.

</div>
