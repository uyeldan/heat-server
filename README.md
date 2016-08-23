# Heat Ledger Server

There is a reason Heat Ledger offers the fastest blockchain solution,
there also is a reason why Heat Ledger Ltd was the first and only one to come 
up with a blockchain that scales literally *endlessly*.

1.000 Petabyte blockchain with over 8 billion active accounts? 

Impossible on literally any other blockchain solution available.

Why is this possible you might ask ... The answer to this lies in the way 
Heat Ledger Ltd understands how to separate the various parts that make up a 
crypto-currency network (or blockchain for that matter).

The problem facing literally *every other blockchain out there* is that they 
don't divide the work in the most optimal way. 

Our believe is that a blockchain is just that.. a chain of blocks. 

- it does not need an HTTP API
- it does not need any databases (especially not embedded databases)
- it does not need to tell you who send how much to whom on 13:03, 7 november 2015.

All those things, we believe belong 'somewhere else'. Somewhere away from the
blockchain, somewhere optimized for this. Somewhere that scales up or down as
we need to.

# Introducing heat-server

Heat-server is our massively scaling blockchain 'front-end' so to say. If you 
need to know any kind of detail of the heat ledger blockchain. 

- account balances
- transactions
- blocks and contents
- etc..

And need to have access to these things in real-time, instantly as soon as 
anything on the network occurs. In that case heat-server is your friend.

# Technology

Heat-server really is a window into the real-time replicated blockchain data
that heat-ledger pumps into (this case) a MySQL database. I say 'in this case'
since its up to you to determine what type of data storage backend and even 
what types of data you live stream from blockchain network to database
backend.

But there is more. 

We not only offer you a passive view into the blockchain originated data, 
you can also subscribe to server side events over WebSockets and be notified of 
all sorts of blockchain events. The heat-server in turn knows how to register 
itself as a WebSocket observer with heat-ledger and thus becomes a forwarding
proxy for events that originate in heat-ledger.

The framework of choice for heat-server is Play! Framework it was chosen for 
several reasons:

- Written in Scala (and thus compatible with the heat-ledger Java source code)
- Highly scalable
- Developer friendly

Here's how Play! says it themselves.

> Play Framework makes it easy to build web applications with Java & Scala.<br>
> Play is based on a lightweight, stateless, web-friendly architecture.<br>
> Built on Akka, Play provides predictable and minimal resource consumption (CPU,
 memory, threads) for highly-scalable applications.

If you haven't done so already we highly recommend taking a look over at their
website, you'll be impressed. No doubt. 

# Getting started

....