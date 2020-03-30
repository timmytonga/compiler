grammar Crux;
program
 : declarationList EOF
 ;

declarationList
 : declaration*
 ;

declaration
 : variableDeclaration
// | arrayDeclaration
// | functionDefinition
 ;

variableDeclaration
 : Var Identifier ':' type ';'
 ;

type
 : Identifier
 ;

literal
 : Integer
 | True
 | False
 ;

SemiColon: ';';
Colon: ':';

Integer
 : '0'
 | [1-9] [0-9]*
 ;

True: 'true';
False: 'false';

Var: 'var';

Identifier
 : [a-zA-Z] [a-zA-Z0-9_]*
 ;

WhiteSpaces
 : [ \t\r\n]+ -> skip
 ;

Comment
 : '//' ~[\r\n]* -> skip
 ;
