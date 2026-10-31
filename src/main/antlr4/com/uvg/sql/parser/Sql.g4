/*
 * Gramatica del subconjunto de SQL que acepta el interprete.
 *
 * Sentencias: CREATE/DROP TABLE, CREATE/DROP INDEX, INSERT, UPDATE, DELETE y
 * SELECT con INNER JOIN, WHERE, GROUP BY, HAVING, ORDER BY, agregaciones y
 * subconsultas (IN, EXISTS y escalares).
 *
 * Las palabras clave y los identificadores no distinguen mayusculas
 * (caseInsensitive); el AstBuilder ademas pasa los identificadores a
 * minusculas para que `Estudiantes` y `estudiantes` sean la misma tabla.
 */
grammar Sql;

options { caseInsensitive = true; }

// ------------------------------------------------------------------ sentencias

// Las sentencias se separan con ';'. El ultimo ';' es opcional y se toleran
// ';' sobrantes, como en cualquier cliente de SQL.
script
    : SEMI* (statement (SEMI+ statement)* SEMI*)? EOF
    ;

statement
    : createTable
    | dropTable
    | createIndex
    | dropIndex
    | insertStmt
    | updateStmt
    | deleteStmt
    | selectStmt
    ;

createTable
    : CREATE TABLE identifier LPAREN columnDef (COMMA columnDef)* RPAREN
    ;

columnDef
    : identifier dataType columnConstraint*
    ;

dataType
    : INT_T                                   # intType
    | FLOAT_T                                 # floatType
    | VARCHAR_T LPAREN INTEGER_LIT RPAREN     # varcharType
    | BOOLEAN_T                               # booleanType
    | DATE_T                                  # dateType
    ;

columnConstraint
    : PRIMARY KEY                             # primaryKeyConstraint
    | NOT NULL                                # notNullConstraint
    ;

dropTable
    : DROP TABLE identifier
    ;

createIndex
    : CREATE INDEX identifier ON identifier LPAREN identifier RPAREN
    ;

dropIndex
    : DROP INDEX identifier
    ;

insertStmt
    : INSERT INTO identifier (LPAREN identifier (COMMA identifier)* RPAREN)?
      VALUES valuesRow (COMMA valuesRow)*
    ;

valuesRow
    : LPAREN expr (COMMA expr)* RPAREN
    ;

updateStmt
    : UPDATE identifier SET assignment (COMMA assignment)* whereClause?
    ;

assignment
    : identifier EQ expr
    ;

deleteStmt
    : DELETE FROM identifier whereClause?
    ;

selectStmt
    : SELECT selectItem (COMMA selectItem)*
      FROM tableRef joinClause*
      whereClause?
      (GROUP BY expr (COMMA expr)*)?
      (HAVING expr)?
      (ORDER BY orderItem (COMMA orderItem)*)?
    ;

selectItem
    : STAR                                    # allColumns
    | identifier DOT STAR                     # tableColumns
    | expr (AS? identifier)?                  # exprItem
    ;

tableRef
    : identifier (AS? identifier)?
    ;

joinClause
    : INNER? JOIN tableRef ON expr
    ;

whereClause
    : WHERE expr
    ;

orderItem
    : expr (ASC | DESC)?
    ;

// ----------------------------------------------------------------- expresiones

// En una regla recursiva por la izquierda ANTLR da mas precedencia a las
// alternativas de arriba: unario > * / % > + - > comparaciones > NOT > AND > OR.
expr
    : primary                                              # primaryExpr
    | op=(MINUS | PLUS) expr                               # unaryExpr
    | expr op=(STAR | SLASH | PERCENT) expr                # mulExpr
    | expr op=(PLUS | MINUS) expr                          # addExpr
    | expr op=(EQ | NEQ | LT | LTE | GT | GTE) expr        # compareExpr
    | expr IS NOT? NULL                                    # isNullExpr
    | expr NOT? IN LPAREN selectStmt RPAREN                # inSubqueryExpr
    | expr NOT? IN LPAREN expr (COMMA expr)* RPAREN        # inListExpr
    | NOT expr                                             # notExpr
    | expr AND expr                                        # andExpr
    | expr OR expr                                         # orExpr
    ;

primary
    : literal                                              # literalPrimary
    | aggregate                                            # aggregatePrimary
    | EXISTS LPAREN selectStmt RPAREN                      # existsPrimary
    | LPAREN selectStmt RPAREN                             # subqueryPrimary
    | LPAREN expr RPAREN                                   # parenPrimary
    | identifier (DOT identifier)?                         # columnPrimary
    ;

aggregate
    : func=(COUNT | SUM | AVG | MIN | MAX) LPAREN (STAR | expr) RPAREN
    ;

literal
    : INTEGER_LIT                                          # intLiteral
    | FLOAT_LIT                                            # floatLiteral
    | STRING                                               # stringLiteral
    | DATE_T STRING                                        # dateLiteral
    | (TRUE | FALSE)                                       # boolLiteral
    | NULL                                                 # nullLiteral
    ;

identifier
    : IDENT
    ;

// ---------------------------------------------------------------------- lexer

CREATE   : 'CREATE';
DROP     : 'DROP';
TABLE    : 'TABLE';
INDEX    : 'INDEX';
ON       : 'ON';
INSERT   : 'INSERT';
INTO     : 'INTO';
VALUES   : 'VALUES';
UPDATE   : 'UPDATE';
SET      : 'SET';
DELETE   : 'DELETE';
SELECT   : 'SELECT';
FROM     : 'FROM';
WHERE    : 'WHERE';
INNER    : 'INNER';
JOIN     : 'JOIN';
GROUP    : 'GROUP';
BY       : 'BY';
HAVING   : 'HAVING';
ORDER    : 'ORDER';
ASC      : 'ASC';
DESC     : 'DESC';
AS       : 'AS';
AND      : 'AND';
OR       : 'OR';
NOT      : 'NOT';
IN       : 'IN';
EXISTS   : 'EXISTS';
IS       : 'IS';
NULL     : 'NULL';
TRUE     : 'TRUE';
FALSE    : 'FALSE';
PRIMARY  : 'PRIMARY';
KEY      : 'KEY';

COUNT    : 'COUNT';
SUM      : 'SUM';
AVG      : 'AVG';
MIN      : 'MIN';
MAX      : 'MAX';

INT_T     : 'INT' | 'INTEGER';
FLOAT_T   : 'FLOAT';
VARCHAR_T : 'VARCHAR';
BOOLEAN_T : 'BOOLEAN';
DATE_T    : 'DATE';

LPAREN  : '(';
RPAREN  : ')';
COMMA   : ',';
SEMI    : ';';
DOT     : '.';
STAR    : '*';
PLUS    : '+';
MINUS   : '-';
SLASH   : '/';
PERCENT : '%';
EQ      : '=';
NEQ     : '<>' | '!=';
LTE     : '<=';
GTE     : '>=';
LT      : '<';
GT      : '>';

FLOAT_LIT   : [0-9]+ '.' [0-9]+;
INTEGER_LIT : [0-9]+;

// Una comilla dentro de la cadena se escribe doble: 'O''Brien'.
STRING : '\'' ( ~['\r\n] | '\'\'' )* '\'';

IDENT : [a-z_] [a-z0-9_]*;

LINE_COMMENT  : '--' ~[\r\n]* -> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;
WS            : [ \t\r\n]+ -> skip;
