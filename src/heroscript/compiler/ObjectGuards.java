package heroscript.compiler;

import java.util.*;

/** Guard intervals preserve evaluation order and evaluate each receiver only once. */
final class ObjectGuards {
    record Range(int start,int end) {}
    private static final Set<String> CONTROL=Set.of("if","while","for","switch","catch","synchronized","try","return","throw","new");
    private static final Set<String> WRITES=Set.of("=","+=","-=","*=","/=","%=","++","--","&=","|=","^=","<<=",">>=",">>>=");

    static Set<Range> collect(String file,List<Token> tokens,Map<String,String> types,Set<Token> declarations,Set<String> fields) {
        Set<Range> ranges=new HashSet<>();
        Set<Integer> shield=new HashSet<>();
        for(int i=0;i+3<tokens.size();i++)if(tokens.get(i).is("shield")&&tokens.get(i+1).is("(")&&tokens.get(i+2).isIdent()&&tokens.get(i+3).is(")"))for(int j=i;j<=i+3;j++)shield.add(j);
        for(int i=0;i<tokens.size();i++) {
            Token token=tokens.get(i);
            if(shield.contains(i)||declarations.contains(token))continue;
            boolean afterDot=i>0&&tokens.get(i-1).is(".");
            String type=types.get(token.text());
            String after=i+1<tokens.size()?tokens.get(i+1).text():"";
            String before=i>0?tokens.get(i-1).text():"";
            if(token.isIdent()&&!afterDot&&type!=null&&!JavaBindings.primitive(type)
                    &&!WRITES.contains(after)&&!before.equals("++")&&!before.equals("--")&&!after.equals("(")
                    &&!before.equals("new")&&!before.equals("instanceof"))ranges.add(new Range(i,i));
            if(token.is(".")||token.is("[")) {
                int end=i-1,start=start(tokens,end);
                if(start<0||shield.contains(start)||declarations.contains(tokens.get(start)))continue;
                Token root=tokens.get(start);
                String rootType=types.get(root.text());
                boolean value=root.is("this")||root.is("new")||root.is("(")||fields.contains(root.text())
                        ||(rootType!=null&&!JavaBindings.primitive(rootType))||tokens.get(end).is(")")||tokens.get(end).is("]");
                boolean unfinishedConstructor=root.is("new")&&!tokens.get(end).is(")");
                if(value&&!root.is("super")&&!unfinishedConstructor)ranges.add(new Range(start,end));
            }
            if(token.is("]")&&!WRITES.contains(after)) {
                int start=start(tokens,i);
                if(start>=0&&!tokens.get(start).is("new")&&!declarations.contains(tokens.get(start)))ranges.add(new Range(start,i));
            }
            if(token.is(")")) {
                int open=matching(tokens,i,"(",")");
                if(open<=0||!tokens.get(open-1).isIdent()||CONTROL.contains(tokens.get(open-1).text())||shield.contains(open-1))continue;
                int start=start(tokens,i);
                if(start<0||declarations.contains(tokens.get(start))||tokens.get(start).is("new"))continue;
                int end=tokens.size()-1;
                while(end>=0&&tokens.get(end).is(";"))end--;
                boolean statement=start==0&&i==end;
                if(!statement&&!forStatementPart(tokens,start,i))ranges.add(new Range(start,i));
            }
        }
        // Only nested/disjoint intervals are legal; reject a crossing interval rather than change expression grouping.
        List<Range> ordered=ranges.stream().sorted(Comparator.comparingInt(Range::start).thenComparing(Comparator.comparingInt(Range::end).reversed())).toList();
        Set<Range> accepted=new LinkedHashSet<>();
        for(Range candidate:ordered) {
            boolean crossing=accepted.stream().anyMatch(previous->previous.start()<candidate.start()&&candidate.start()<=previous.end()&&previous.end()<candidate.end());
            if(crossing)throw new CompileError(file,tokens.get(candidate.start()),"forma de expressão Java ainda não suportada com batplan");
            accepted.add(candidate);
        }
        return accepted;
    }
    private static boolean forStatementPart(List<Token> tokens,int start,int end) {
        if(tokens.isEmpty()||!tokens.get(0).is("for"))return false;
        int depth=0,section=0;
        for(int i=0;i<start;i++) {
            if(tokens.get(i).is("("))depth++;
            if(tokens.get(i).is(")"))depth--;
            if(tokens.get(i).is(";")&&depth==1)section++;
        }
        return depth==1&&section!=1;
    }
    private static int start(List<Token> tokens,int end) {
        if(end<0)return -1;
        Token token=tokens.get(end);
        int start=end;
        if(token.is("]")) {
            int open=matching(tokens,end,"[","]");
            return open>0?start(tokens,open-1):-1;
        }
        if(token.is(")")) {
            int open=matching(tokens,end,"(",")");
            if(open<0)return -1;
            if(open>0&&tokens.get(open-1).isIdent()&&!CONTROL.contains(tokens.get(open-1).text()))start=open-1;
            else if(open>0&&tokens.get(open-1).text().matches(">{1,3}")) {
                int angle=0,at=open-1;
                for(;at>=0;at--) {
                    String text=tokens.get(at).text();
                    if(text.matches(">{1,3}"))angle+=text.length();
                    if(text.equals("<")&&--angle==0)break;
                }
                if(at<=0)return -1;
                start=at-1;
            }
            else start=open;
        }
        while(start>1&&tokens.get(start-1).is(".")) {
            int previous=start(tokens,start-2);
            if(previous<0)break;
            start=previous;
        }
        if(start>0&&tokens.get(start-1).is("new"))start--;
        return start;
    }
    private static int matching(List<Token> tokens,int end,String open,String close) {
        int depth=0;
        for(int i=end;i>=0;i--) {
            if(tokens.get(i).is(close))depth++;
            if(tokens.get(i).is(open)&&--depth==0)return i;
        }
        return -1;
    }
}
