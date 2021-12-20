package org.checkerframework.checker.resourceleak;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import org.checkerframework.checker.mustcall.MustCallAnnotatedTypeFactory;
import org.checkerframework.checker.mustcall.MustCallChecker;
import org.checkerframework.checker.mustcall.qual.MustCall;
import org.checkerframework.checker.mustcall.qual.MustCallAlias;
import org.checkerframework.checker.mustcall.qual.Owning;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.wholeprograminference.WholeProgramInference;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.SingleSuccessorBlock;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.dataflow.expression.LocalVariable;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypeSystemError;
import org.checkerframework.javacutil.TypesUtils;

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

  /** The set of owning fields. */
  private Set<Element> owningFields = new HashSet<>();

  /** The control flow graph. */
  private ControlFlowGraph cfg;

  private boolean isConstructor;

//  private final MustCallConsistencyAnalyzer mccAnalyzer;


  /* package-private */ static class Obligation {

    public final ImmutableSet<ResourceAlias> resourceAliases;

    /**
     * Create an Obligation from a set of resource aliases.
     *
     * @param resourceAliases a set of resource aliases
     */
    public Obligation(Set<ResourceAlias> resourceAliases) {
      this.resourceAliases = ImmutableSet.copyOf(resourceAliases);
    }

    /**
     * Returns the resource alias in this Obligation's resource alias set corresponding to {@code
     * localVariableNode} if one is present. Otherwise, returns null.
     *
     * @param localVariableNode a local variable
     * @return the resource alias corresponding to {@code localVariableNode} if one is present;
     *     otherwise, null
     */
    private @Nullable ResourceAlias getResourceAlias(LocalVariableNode localVariableNode) {
      Element element = localVariableNode.getElement();
      for (ResourceAlias alias : resourceAliases) {
        if (alias.reference.getElement().equals(element)) {
          return alias;
        }
      }
      return null;
    }


    /**
     * Returns true if this contains a resource alias corresponding to {@code localVariableNode},
     * meaning that calling the required methods on {@code localVariableNode} is sufficient to
     * satisfy the must-call obligation this object represents.
     *
     * @param localVariableNode a local variable node
     * @return true if a resource alias corresponding to {@code localVariableNode} is present
     */
    private boolean canBeSatisfiedThrough(LocalVariableNode localVariableNode) {
      return getResourceAlias(localVariableNode) != null;
    }


//    public @Nullable List<String> getMustCallMethods(
//        ResourceLeakAnnotatedTypeFactory rlAtf, @Nullable CFStore mcStore) {
//      MustCallAnnotatedTypeFactory mustCallAnnotatedTypeFactory =
//          rlAtf.getTypeFactoryOfSubchecker(MustCallChecker.class);
//
//      // Need to get the LUB (ie, union) of the MC values, because if a CreatesMustCallFor method
//      // was called on just one of the aliases then they all need to be treated as if they need to
//      // call the relevant methods.
//      AnnotationMirror mcLub = mustCallAnnotatedTypeFactory.BOTTOM;
//      for (ResourceAlias alias : this.resourceAliases) {
//        AnnotationMirror mcAnno = getMustCallValue(alias, mcStore, mustCallAnnotatedTypeFactory);
//        mcLub = mustCallAnnotatedTypeFactory.getQualifierHierarchy().leastUpperBound(mcLub, mcAnno);
//      }
//      if (AnnotationUtils.areSameByName(
//          mcLub, "org.checkerframework.checker.mustcall.qual.MustCall")) {
//        return rlAtf.getMustCallValues(mcLub);
//      } else {
//        return null;
//      }
//    }


//    private static AnnotationMirror getMustCallValue(
//        ResourceAlias alias, @Nullable CFStore mcStore, MustCallAnnotatedTypeFactory mcAtf) {
//      LocalVariable reference = alias.reference;
//      CFValue value = mcStore == null ? null : mcStore.getValue(reference);
//      if (value != null) {
//        AnnotationMirror result =
//            AnnotationUtils.getAnnotationByClass(value.getAnnotations(), MustCall.class);
//        if (result != null) {
//          return result;
//        }
//      }
//      // There wasn't an @MustCall annotation for it in the store, so fall back to the default
//      // must-call type for the class.
//      // TODO: we currently end up in this case when checking a call to the return type
//      // of a returns-receiver method on something with a MustCall type; for example,
//      // see tests/socket/ZookeeperReport6.java. We should instead use a poly type if we can.
//      TypeElement typeElt = TypesUtils.getTypeElement(reference.getType());
//      if (typeElt == null) {
//        // typeElt is null if reference.getType() was not a class, interface, annotation type,
//        // or enum -- that is, was not an annotatable type.
//        // That happens rarely, such as when it is a wildcard type. In these cases, fall back
//        // on a safe default: top.
//        return mcAtf.TOP;
//      }
//      if (typeElt.asType().getKind() == TypeKind.VOID) {
//        // Void types can't have methods called on them, so returning bottom is safe.
//        return mcAtf.BOTTOM;
//      }
//      return mcAtf.getAnnotatedType(typeElt).getAnnotationInHierarchy(mcAtf.TOP);
//    }

    @Override
    public String toString() {
      return "Obligation: resourceAliases=" + Iterables.toString(resourceAliases);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Obligation that = (Obligation) obj;
      return this.resourceAliases.equals(that.resourceAliases);
    }

    @Override
    public int hashCode() {
      return Objects.hash(resourceAliases);
    }
  }

  /* package-private */ static class ResourceAlias {

    /** A local variable defined in the source code or a temporary variable for an expression. */
    public final LocalVariable reference;

    public final Tree tree;

    /**
     * Create a new resource alias.
     *
     * @param reference the local variable
     * @param tree the tree
     */
    public ResourceAlias(LocalVariable reference, Tree tree) {
      this.reference = reference;
      this.tree = tree;
    }

    @Override
    public String toString() {
      return "(ResourceAlias: reference: " + reference + " |||| tree: " + tree + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ResourceAlias that = (ResourceAlias) o;
      return reference.equals(that.reference) && tree.equals(that.tree);
    }

    @Override
    public int hashCode() {
      return Objects.hash(reference, tree);
    }
  }

  /**
   * Creates a MustCallInferenceLogic. If the whole program inference is not null, the type
   * factory's postAnalyze method would instantiate a new MustCallInferenceLogic using this
   * constructor and then call {@link #runInference()}.
   *
   * @param typeFactory the type factory
   * @param cfg the ControlFlowGraph
   */
  MustCallInferenceLogic(
      ResourceLeakAnnotatedTypeFactory typeFactory,
      ControlFlowGraph cfg) {
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

  private Set<Obligation> getNonEmptyMCParams(ControlFlowGraph cfg) {
    // TODO what about lambdas?
    if (cfg.getUnderlyingAST().getKind() == UnderlyingAST.Kind.METHOD) {
      MethodTree method = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
      Set<Obligation> result = new LinkedHashSet<>(1);
      for (VariableTree param : method.getParameters()) {
        Element paramElement = TreeUtils.elementFromDeclaration(param);
        if (typeFactory.declaredTypeHasMustCall(param)
            && paramElement.getAnnotation(Owning.class) == null && !typeFactory.hasMustCallAlias(paramElement)) {
          result.add(
              new Obligation(
                  ImmutableSet.of(new ResourceAlias(new LocalVariable(paramElement), param))));
        }
      }
      return result;
    }
    return Collections.emptySet();
  }

  /**
   * Runs the inference algorithm on the contents of the {@link #cfg} field.
   *
   * <p>Operationally, it checks method invocations for fields with non-empty @MustCall obligations
   * along all paths to the regular exit point in the method body of the method represented by
   * {@link #cfg}, and updates the {@link #owningFields} set if it discovers an owning field whose
   * must-call obligations were satisfied along one of the checked paths.
   */
  void runInference() {

    Set<BlockWithObligations> visited = new HashSet<>();
    Deque<BlockWithObligations> worklist = new ArrayDeque<>();

    BlockWithObligations entry =
        new BlockWithObligations(cfg.getEntryBlock(), getNonEmptyMCParams(cfg));

    worklist.add(entry);
    visited.add(entry);

    while (!worklist.isEmpty()) {
      BlockWithObligations current = worklist.remove();

      Set<Obligation> obligations = new LinkedHashSet<>(current.obligations);

      for (Node node : current.block.getNodes()) {
        if (node instanceof MethodInvocationNode || node instanceof ObjectCreationNode) {
          updateMethodInvocationOrObjectCreationNode(obligations, node);
          if (node instanceof MethodInvocationNode) {
            checkForMustCallInvocationOnField((MethodInvocationNode) node);
          }
        } else if (node instanceof AssignmentNode) {
          updateObligationsForAssignment(obligations, (AssignmentNode) node);
        } else if (node instanceof ReturnNode) {
          checkReturnNode(obligations, (ReturnNode) node);
        }
      }

      propagateRegPaths(obligations, current.block, visited, worklist);
    }
  }

  private void updateMethodInvocationOrObjectCreationNode(Set<Obligation> obligations, Node node) {
    List<Node> arguments = getArgumentsOfInvocation(node);
    List<? extends VariableElement> parameters = getParametersOfInvocation(node);

    for (int i = 0; i < arguments.size(); i++) {
      Obligation obligation = getObligationForVar(obligations, (LocalVariableNode) arguments.get(i));
      if (obligation != null) {
        Node method = getTempVarOrNode(node);
        if (typeFactory.hasMustCallAlias(parameters.get(i))) {
          Set<ResourceAlias> newResourceAliasesForObligation = new LinkedHashSet<>(obligation.resourceAliases);
          newResourceAliasesForObligation.add(new ResourceAlias(new LocalVariable((LocalVariableNode) method), node.getTree()));
          obligations.remove(obligation);
          obligations.add(new Obligation(newResourceAliasesForObligation));
        }
      }
    }
  }

  private List<? extends VariableElement> getParametersOfInvocation(Node node) {
    ExecutableElement executableElement;
    if (node instanceof MethodInvocationNode) {
      MethodInvocationNode invocationNode = (MethodInvocationNode) node;
      executableElement = TreeUtils.elementFromUse(invocationNode.getTree());
    } else if (node instanceof ObjectCreationNode) {
      executableElement = TreeUtils.elementFromUse(((ObjectCreationNode) node).getTree());
    } else {
      throw new TypeSystemError("unexpected node type " + node.getClass());
    }

    return executableElement.getParameters();
  }

  private List<Node> getArgumentsOfInvocation(Node node) {
    if (node instanceof MethodInvocationNode) {
      MethodInvocationNode invocationNode = (MethodInvocationNode) node;
      return invocationNode.getArguments();
    } else if (node instanceof ObjectCreationNode) {
      return ((ObjectCreationNode) node).getArguments();
    } else {
      throw new TypeSystemError("unexpected node type " + node.getClass());
    }
  }

  private void checkReturnNode(Set<Obligation> obligations, ReturnNode node) {
    MethodTree method = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    List<? extends VariableTree> params = method.getParameters();
    Node returnNode = node.getResult();
    returnNode = getTempVarOrNode(returnNode);
    Obligation returnObligation = getObligationForVar(obligations, (LocalVariableNode) returnNode);
    if (returnObligation != null) {

      Set<ResourceAlias> returnAliases = returnObligation.resourceAliases;
      for (ResourceAlias rl : returnAliases) {
        Element returnElt = rl.reference.getElement();
        for (int i = 0; i < params.size(); i++) {
          VariableElement paramElt = TreeUtils.elementFromDeclaration(params.get(i));
          if (returnElt.equals(paramElt)) {
            ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
            WholeProgramInference wpi = typeFactory.getWholeProgramInference();
            wpi.addMethodDeclarationAnnotation(methodElt, MUSTCALLALIAS);
            wpi.addDeclarationAnnotationToFormalParameter(methodElt, i, MUSTCALLALIAS);
          }
        }
      }
    }
  }

  private void updateObligationsForAssignment(
      Set<Obligation> obligations, AssignmentNode assignmentNode) {
    Node lhs = assignmentNode.getTarget();
    Element lhsElement = TreeUtils.elementFromTree(lhs.getTree());
    // Use the temporary variable for the rhs if it exists.
    Node rhs = NodeUtils.removeCasts(assignmentNode.getExpression());
    rhs = getTempVarOrNode(rhs);

    MethodTree method = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    Obligation rhsObligation = getObligationForVar(obligations, (LocalVariableNode) rhs);

    if (rhsObligation != null && lhsElement.getKind() == ElementKind.FIELD && isConstructor) {
      if (typeFactory.getDeclAnnotation(lhsElement, Owning.class) != null) {

          Set<ResourceAlias> rhsAliases = rhsObligation.resourceAliases;
          for (ResourceAlias rl : rhsAliases) {
            Element rhdElt = rl.reference.getElement();
            List<? extends VariableTree> params = method.getParameters();
            for (int i = 0; i < params.size(); i++) {
              VariableElement paramElt = TreeUtils.elementFromDeclaration(params.get(i));
              if (paramElt.equals(rhdElt)) {
                ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
                WholeProgramInference wpi = typeFactory.getWholeProgramInference();
                wpi.addMethodDeclarationAnnotation(methodElt, MUSTCALLALIAS);
                wpi.addDeclarationAnnotationToFormalParameter(methodElt, i, MUSTCALLALIAS);
                removeObligationsContainingVar(obligations, (LocalVariableNode) rhs);
                break;
              }
            }
          }
      }

    } else if (rhsObligation != null && lhs instanceof LocalVariableNode) {
      LocalVariableNode lhsVar = (LocalVariableNode) lhs;
      Obligation lhsObligation = getObligationForVar(obligations, lhsVar);
      if (lhsObligation == null) {
        Set<ResourceAlias> newResourceAliasesForObligation = new LinkedHashSet<>(rhsObligation.resourceAliases);
        newResourceAliasesForObligation.add(new ResourceAlias(new LocalVariable(lhsVar), lhs.getTree()));
        obligations.remove(rhsObligation);
        obligations.add(new Obligation(newResourceAliasesForObligation));
      } else {
        // TODO killing assignment
      }
    }
  }

  private void removeObligationsContainingVar(Set<Obligation> obligations, LocalVariableNode var) {
    Obligation obligationForVar = getObligationForVar(obligations, var);
    while (obligationForVar != null) {
      obligations.remove(obligationForVar);
      obligationForVar = getObligationForVar(obligations, var);
    }
  }

  private static @Nullable Obligation getObligationForVar(
      Set<Obligation> obligations, LocalVariableNode node) {
    for (Obligation obligation : obligations) {
      if (obligation.canBeSatisfiedThrough(node)) {
        return obligation;
      }
    }
    return null;
  }

  private Node getTempVarOrNode(final Node node) {
    Node temp = typeFactory.getTempVarForNode(node);
    if (temp != null) {
      return temp;
    }
    return node;
  }

  /**
   * If the receiver of {@code mNode} is a candidate owning field and the method invocation
   * satisfies the field's must-call obligation, then adds that field to the {@link #owningFields}
   * set.
   *
   * @param mNode the MethodInvocationNode
   */
  private void checkForMustCallInvocationOnField(MethodInvocationNode mNode) {
    Node receiver = mNode.getTarget().getReceiver();
    if (receiver.getTree() == null) {
      return;
    }

    Element receiverEl = TreeUtils.elementFromTree(receiver.getTree());

    if (receiverEl != null && typeFactory.isCandidateOwningField(receiverEl)) {
      Element method = TreeUtils.elementFromTree(mNode.getTree());
      List<String> mustCallValues = typeFactory.getMustCallValue(receiverEl);

      // This assumes that any MustCall annotation has at most one element.
      // TODO: generalize this to MustCall annotations with more than one element.
      if (mustCallValues.size() == 1
          && mustCallValues.contains(method.getSimpleName().toString())) {
        owningFields.add(receiverEl);
      }
    }
  }

  /**
   * Updates {@code worklist} with the next block along all paths to the regular exit point. If the
   * next block is a regular exit point, adds an {@literal @}Owning annotation for fields in {@link
   * #owningFields}.
   *
   * @param curBlock the current block
   * @param visited set of blocks already on the worklist
   * @param worklist current worklist
   */
  private void propagateRegPaths(Set<Obligation> obligations, Block curBlock, Set<BlockWithObligations> visited, Deque<BlockWithObligations> worklist) {

    List<Block> successors = getNormalSuccessors(curBlock);

    for (Block successor : successors) {
      // If b is a special block, it must be the regular exit, since we do not propagate to
      // exceptional successors.
      if (successor.getType() == Block.BlockType.SPECIAL_BLOCK) {
        WholeProgramInference wpi = typeFactory.getWholeProgramInference();
        assert wpi != null : "MustCallInference is running without WPI.";
        for (Element fieldElt : owningFields) {
          wpi.addFieldDeclarationAnnotation(fieldElt, OWNING);
        }
      }
      BlockWithObligations state = new BlockWithObligations(successor, obligations);
      if (visited.add(state)) {
        worklist.add(state);
      }
    }
  }

  /**
   * Returns the non-exceptional successors of the current block.
   *
   * @param cur the current block
   * @return the successors of this current block
   */
  private List<Block> getNormalSuccessors(Block cur) {
    List<Block> successorBlock = new ArrayList<>();

    if (cur.getType() == Block.BlockType.CONDITIONAL_BLOCK) {

      ConditionalBlock ccur = (ConditionalBlock) cur;

      successorBlock.add(ccur.getThenSuccessor());
      successorBlock.add(ccur.getElseSuccessor());

    } else {
      if (!(cur instanceof SingleSuccessorBlock)) {
        throw new BugInCF("BlockImpl is neither a conditional block nor a SingleSuccessorBlock");
      }

      Block b = ((SingleSuccessorBlock) cur).getSuccessor();
      if (b != null) {
        successorBlock.add(b);
      }
    }
    return successorBlock;
  }

  private static class BlockWithObligations {

    /** The block. */
    public final Block block;

    /** The dataflow facts. */
    public final ImmutableSet<Obligation> obligations;

    /**
     * Create a new BlockWithObligations from a block and a set of dataflow facts.
     *
     * @param b the block
     * @param obligations the set of incoming Obligations at the start of the block (may be the
     *     empty set)
     */
    public BlockWithObligations(Block b, Set<Obligation> obligations) {
      this.block = b;
      this.obligations = ImmutableSet.copyOf(obligations);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BlockWithObligations that = (BlockWithObligations) o;
      return block.equals(that.block) && obligations.equals(that.obligations);
    }

    @Override
    public int hashCode() {
      return Objects.hash(block, obligations);
    }
  }
}
