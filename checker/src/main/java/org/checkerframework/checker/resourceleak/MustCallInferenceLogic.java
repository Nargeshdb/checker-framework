package org.checkerframework.checker.resourceleak;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import org.checkerframework.checker.mustcall.qual.MustCallAlias;
import org.checkerframework.checker.mustcall.qual.Owning;
import org.checkerframework.common.wholeprograminference.WholeProgramInference;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;

/**
 * This class contains Resource Leak Checker annotation inference algorithm. For now, it just
 * contains inference logic for @Owning annotations on final owning fields. It adds @Owning
 * annotation on a field if it finds a method that satisfies @MustCall obligation of the field along
 * some path to the regular exit point.
 */
public class MustCallInferenceLogic {

  /**
   * The type factory for the Resource Leak Checker, which is used to access the Must Call Checker.
   */
  private final ResourceLeakAnnotatedTypeFactory typeFactory;

  /** The @Owning annotation. */
  protected final AnnotationMirror OWNING;

  protected final AnnotationMirror MUSTCALLALIAS;

  /** The control flow graph. */
  private ControlFlowGraph cfg;

  private boolean isConstructor;

  /**
   * Creates a MustCallInferenceLogic. If the whole program inference is not null, the type
   * factory's postAnalyze method would instantiate a new MustCallInferenceLogic using this
   * constructor and then call {@link #inference()}.
   *
   * @param typeFactory the type factory
   * @param cfg the ControlFlowGraph
   */
  MustCallInferenceLogic(ResourceLeakAnnotatedTypeFactory typeFactory, ControlFlowGraph cfg) {
    this.typeFactory = typeFactory;
    this.cfg = cfg;
    OWNING = AnnotationBuilder.fromClass(this.typeFactory.getElementUtils(), Owning.class);
    MUSTCALLALIAS =
        AnnotationBuilder.fromClass(this.typeFactory.getElementUtils(), MustCallAlias.class);
    isConstructor =
        ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST())
            .getMethod()
            .getName()
            .toString()
            .equals("<init>");
  }

  /**
   * It checks method invocations for fields with non-empty @MustCall obligation along all paths to
   * the regular exit point in the method body.
   */
  void inference() {

    Set<Block> visited = new HashSet<>();
    Deque<Block> worklist = new ArrayDeque<>();
    Block entry = this.cfg.getEntryBlock();
    worklist.add(entry);
    visited.add(entry);

    while (!worklist.isEmpty()) {
      Block current = worklist.remove();

      for (Node node : current.getNodes()) {
        if (node instanceof MethodInvocationNode) {
          checksOwningFieldForInvocation((MethodInvocationNode) node);
        } else if (isConstructor && node instanceof AssignmentNode) {
          MethodTree method = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
          AssignmentNode assignmentNode = (AssignmentNode) node;
          Node lhs = assignmentNode.getTarget();
          Element lhsElement = TreeUtils.elementFromTree(lhs.getTree());
          if (typeFactory.getDeclAnnotation(lhsElement, Owning.class) != null) {
            Node rhs = NodeUtils.removeCasts(assignmentNode.getExpression());
            Element rhdElt = TreeUtils.elementFromTree(rhs.getTree());
            List<? extends VariableTree> params = method.getParameters();
            for (int i = 0; i < params.size(); i++) {
              VariableElement paramElt = TreeUtils.elementFromDeclaration(params.get(i));
              if (paramElt.equals(rhdElt)) {
                ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
                WholeProgramInference wpi = typeFactory.getWholeProgramInference();
                wpi.addMethodDeclarationAnnotation(methodElt, MUSTCALLALIAS);
                wpi.addDeclarationAnnotationToFormalParameter(methodElt, i, MUSTCALLALIAS);
                break;
              }
            }
          }
        }
      }

      propagateRegPaths(current, visited, worklist);
    }
  }

  /**
   * If the receiver of {@code mNode} is a possible owning field and the method invocation satisfies
   * the field's must call obligation, then adds owning annotation for that field.
   *
   * @param mNode the MethodInvocationNode
   */
  private void checksOwningFieldForInvocation(MethodInvocationNode mNode) {
    Node receiver = mNode.getTarget().getReceiver();
    if (receiver.getTree() != null) {
      Element receiverEl = TreeUtils.elementFromTree(receiver.getTree());
      if (typeFactory.isElementPossibleOwningField(receiverEl)) {
        Element method = TreeUtils.elementFromTree(mNode.getTree());
        List<String> mustCallValues = typeFactory.getMustCallValue(receiverEl);
        if (mustCallValues.size() == 1
            && mustCallValues.get(0).equals(method.getSimpleName().toString())) {
          WholeProgramInference wpi = typeFactory.getWholeProgramInference();
          if (wpi != null) {
            wpi.addFieldDeclarationAnnotation(receiverEl, OWNING);
          }
        }
      }
    }
  }

  /**
   * updates worklist with the next block along all paths to the regular exit point.
   *
   * @param curBlock the current block
   * @param visited block pairs already on the worklist
   * @param worklist current worklist
   */
  private void propagateRegPaths(Block curBlock, Set<Block> visited, Deque<Block> worklist) {

    Set<Block> successors = curBlock.getSuccessors();

    for (Block b : successors) {
      if (b.getType() == Block.BlockType.SPECIAL_BLOCK) {

        if (((SpecialBlock) b).getSpecialType() == SpecialBlock.SpecialBlockType.EXCEPTIONAL_EXIT) {
          // Do nothing if the successor block is an exceptional exit point
          continue;
        }
      }
      if (visited.add(b)) {
        worklist.add(b);
      }
    }
  }
}
