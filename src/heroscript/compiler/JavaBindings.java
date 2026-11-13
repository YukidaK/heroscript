package heroscript.compiler;

import java.util.*;

/** Declaration metadata for explicit Java types, including qualified, generic and array references. */
final class JavaBindings {
    record Binding(Token name,String type,List<Token> typeTokens) {}
    private static final Set<String> MODIFIERS=Set.of("public","private","protected","static","final","transient","volatile");
    private static final Set<String> NOT_TYPES=Set.of("return","throw","new","if","else","for","while","do","switch","case","default","try","catch","finally","this","super","break","continue","instanceof","true","false","null");

    static List<Binding> collect(List<Token> tokens) {
        List<Binding> result=new ArrayList<>();
        Set<Integer> names=new HashSet<>();
        for(int i=0;i<tokens.size();i++) {
            Token first=tokens.get(i);
            if(!first.isIdent()||MODIFIERS.contains(first.text())||NOT_TYPES.contains(first.text()))continue;
            if(i>0&&!Set.of("(",",",";","final","public","private","protected","static","transient","volatile").contains(tokens.get(i-1).text()))continue;
            int end=i+1;
            while(end+1<tokens.size()&&tokens.get(end).is(".")&&tokens.get(end+1).isIdent())end+=2;
            if(end<tokens.size()&&tokens.get(end).is("<")) {
                int depth=0;
                do {
                    String text=tokens.get(end++).text();
                    if(text.equals("<"))depth++;
                    if(text.matches(">{1,3}"))depth-=text.length();
                } while(end<tokens.size()&&depth>0);
                if(depth!=0)continue;
            }
            while(end+1<tokens.size()&&tokens.get(end).is("[")&&tokens.get(end+1).is("]"))end+=2;
            if(end>=tokens.size()||!tokens.get(end).isIdent())continue;
            int name=end,suffix=end+1;
            while(suffix+1<tokens.size()&&tokens.get(suffix).is("[")&&tokens.get(suffix+1).is("]"))suffix+=2;
            if(suffix<tokens.size()&&!Set.of("=",";",",",":",")").contains(tokens.get(suffix).text()))continue;
            if(!names.add(name))continue;
            String base=compact(tokens.subList(i,end));
            List<Token> type=tokens.subList(i,end);
            result.add(new Binding(tokens.get(name),base+compact(tokens.subList(name+1,suffix)),type));
            int parens=0,brackets=0,generics=0;boolean afterNew=false;
            for(int j=suffix;j<tokens.size();j++) {
                Token token=tokens.get(j);
                if((token.is(";")||token.is(")")||token.is(":"))&&parens==0&&brackets==0)break;
                if(token.is("new"))afterNew=true;
                if(token.is("(")||token.is("{")){parens++;afterNew=false;}
                if(token.is(")")||token.is("}"))parens--;
                if(token.is("["))brackets++;
                if(token.is("]"))brackets--;
                if(afterNew||generics>0) {
                    if(token.is("<"))generics++;
                    if(token.text().matches(">{1,3}"))generics=Math.max(0,generics-token.text().length());
                }
                if(token.is(",")&&parens==0&&brackets==0&&generics==0&&j+1<tokens.size()) {
                    int next=j+1,tail=next+1;
                    if(!tokens.get(next).isIdent())continue;
                    while(tail+1<tokens.size()&&tokens.get(tail).is("[")&&tokens.get(tail+1).is("]"))tail+=2;
                    if(tail<tokens.size()&&!Set.of("=",";",",",")").contains(tokens.get(tail).text()))break;
                    if(names.add(next))result.add(new Binding(tokens.get(next),base+compact(tokens.subList(next+1,tail)),type));
                    afterNew=false;j=tail-1;
                }
            }
        }
        return result;
    }
    static boolean primitive(String type) {return Set.of("boolean","byte","short","int","long","float","double","char").contains(type);}
    private static String compact(List<Token> tokens) {return String.join("",tokens.stream().map(Token::text).toList());}
}
