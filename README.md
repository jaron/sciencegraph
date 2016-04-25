# ScienceGraph

A comprehensive knowledge graph of scientific concepts

### Introduction

The ScienceGraph knowledge graph is distributed as a Neo4j database, and currently consists of about 1.4 million nodes
and 2.4 million relationships. Much of the data comes from the general knowledge [ConceptNet 5](http://conceptnet5.media.mit.edu) project,
which was then curated to filter out much of its non-scientific content to produce a more focused, domain-specific knowledge graph.

ScienceGraph was compiled for the purpose of answering scientific questions using natural language. This repo includes two collections of
sample questions graciously released by the [Allen Institute of AI](http://allenai.org/data.html), containing science exam questions posed
to 11 and 15 year-olds. Instructions for running the question answering code are provided below.


##### Getting Started

* If you haven't already, download and install the [Neo4j graph database](http://neo4j.com/download/) (The free community edition is fine)
* Now clone this repo, and unzip the sciencegraph.db.zip file
* Start the Neo4j server, and when prompted for the Database Location, select the sciencegraph.db directory you have just unzipped. Then click Start, and wait for a few moments whilst the server initialises.
* The Neo4j server dialog will report when the server is ready, you can know access your graph database's web interface at http://localhost:7474/browser/


##### Sample Queries

. . .



##### Acknowledgements

ScienceGraph includes data from [ConceptNet 5](http://conceptnet5.media.mit.edu), which was compiled by the Commonsense Computing Initiative. ConceptNet 5 is freely available under the Creative Commons Attribution-ShareAlike license (CC BY SA 3.0).