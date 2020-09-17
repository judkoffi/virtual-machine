Notes:

- AST : Abstract Syntaxic Tree 
- Interpreter AST: execute l'AST pour chaque feuille de l'arbre
- Interpreter a pile (stack): Tranformer l'AST en instructions et boucler sur les instructions
- Generer le byte code java a partir de l'AST, le soucis est que le byte code est typer




<h2>Exercice 1 - AST interpreter</h2>

1.Before starting, explain to yourself how the visitor in ASTInterpreter.java works?  
  how to call it, how to do a recursive call?   
  What does the second parameter of the lambdas (env) mean?   
  
- Visitor design pattern is to the configure handler for different type of message 
- To call it, we can just call method <i>visit</i> on Visitor objet. For a recursive call, we can just recall visit method in visit method  
- 
  
