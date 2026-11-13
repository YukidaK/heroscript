package heroscript.compiler;

import heroscript.compiler.Ast.*;
import java.util.ArrayList;
import java.util.List;

/** Select lexical blackwidow regions without evaluating their enclosing instructions. */
final class HulkRegions {
    private HulkRegions() {}
    static Block executable(Block original) {
        List<Stmt> regions=new ArrayList<>();
        collect(original,regions);
        return new Block(original.open(),List.copyOf(regions));
    }
    private static void collect(Block block,List<Stmt> regions) {
        for(Stmt statement:block.stmts()) {
            if(statement instanceof BlackWidow widow)regions.add(widow);
            else if(statement instanceof Block nested)collect(nested,regions);
            else if(statement instanceof Raw raw&&raw.body()!=null)collect(raw.body(),regions);
            else if(statement instanceof DeadpoolAction action)collect(action.body(),regions);
            else if(statement instanceof FlashPoint point)collect(point.body(),regions);
            // A nested hulksmash owns its regions; it only runs from an active blackwidow.
        }
    }
}
