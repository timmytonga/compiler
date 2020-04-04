grammar Crux;

/*
    Parser rules
*/

program         : declarationList EOF;

literal         : Integer | True | False;
designator      : Identifier (Open_Bracket expression0 Close_Bracket)*;
type            : Identifier;

op0             : Greater_equal | Lesser_equal | Not_equal | Equal | Greater_than | Less_than;
op1             : Add | Sub | Or;
op2             : Mul | Div | And;

expression0     : expression1 (op0 expression1)?;
expression1     : expression2 (op1 expression2)*;
expression2     : expression3 (op2 expression3)*;
expression3     : Not expression3 | Open_Paren expression0 Close_Paren | designator | callExpression | literal;

callExpression  : Call Identifier Open_Paren expressionList Close_Paren;
expressionList  : (expression0 (Comma expression0)*)?;

parameter       : Identifier Colon type;
parameterList   : (parameter (Comma parameter)*)?;

variableDeclaration : Var Identifier Colon type SemiColon;
arrayDeclaration    : Array Identifier Colon type Open_Bracket Integer Close_Bracket SemiColon;
functionDefinition  : Func Identifier Open_Paren parameterList Close_Paren Colon type statementBlock;
declaration     : variableDeclaration | arrayDeclaration | functionDefinition;
declarationList : declaration*;

assignmentStatement : Let designator Equal expression0 SemiColon;
callStatement   : callExpression SemiColon;
ifStatement     : If expression0 statementBlock (Else statementBlock);
whileStatement  : While expression0 statementBlock;
returnStatement : Return expression0 SemiColon;
statement       : variableDeclaration | callStatement | assignmentStatement | ifStatement | whileStatement | returnStatement;
statementList   : (statement)*;
statementBlock  : Open_Brace statementList Close_Brace;

 /*
    Lexer rules
 */

/* Reserved keywords  */
And     : 'and';
Or      : 'or';
Not     : 'not';
Let     : 'let';
Var     : 'var';
Array   : 'array';
Func    : 'func';
If      : 'if';
Else    : 'else';
While   : 'while';
True    : 'true';
False   : 'false';
Return  : 'return';

/* Special meaning character sequences */
Open_Paren  : '(';
Close_Paren : ')';
Open_Brace  : '{';
Close_Brace : '}';
Open_Bracket: '[';
Close_Bracket: ']';
Add         : '+';
Sub         : '-';
Mul         : '*';
Div         : '/';
Greater_equal: '>=';
Lesser_equal: '<=';
Not_equal   : '!=';
Equal       : '==';
Greater_than: '>';
Less_than   : '<';
Assign      : '=';
Comma       : ',';
SemiColon   : ';';
Colon       : ':';
Call        : '::';

/* Reserved value literals patterns */
Identifier  : [a-zA-Z] [a-zA-Z0-9_]*;
Integer     : '0' | [1-9] [0-9]*;

/* Skip */
WhiteSpaces : [ \t\r\n]+ -> skip;
Comment     : '//' ~[\r\n]* -> skip;
