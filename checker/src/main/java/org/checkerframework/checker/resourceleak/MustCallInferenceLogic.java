package org.checkerframework.checker.resourceleak;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.*;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
import org.checkerframework.checker.mustcall.qual.CreatesMustCallFor;
import org.checkerframework.checker.mustcall.qual.InheritableMustCall;
import org.checkerframework.checker.mustcall.qual.MustCallAlias;
import org.checkerframework.checker.mustcall.qual.Owning;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.wholeprograminference.WholeProgramInference;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.SingleSuccessorBlock;
import org.checkerframework.dataflow.cfg.node.*;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.dataflow.expression.LocalVariable;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypeSystemError;

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
  private final ResourceLeakAnnotatedTypeFactory rTypeFactory;

  /** The @Owning annotation. */
  protected final AnnotationMirror OWNING;

  /** The @MustCall annotation. */
  protected final AnnotationMirror MUSTCALL;

  /** The @MustCallAlias annotation. */
  protected final AnnotationMirror MUSTCALLALIAS;

  /** The set of owning fields. */
  private Set<VariableElement> owningFields = new HashSet<>();

  private Set<VariableElement> owningFieldToECM = new HashSet<>();

  /** The control flow graph. */
  private final ControlFlowGraph cfg;

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
  /*package-private*/ MustCallInferenceLogic(
      ResourceLeakAnnotatedTypeFactory typeFactory, ControlFlowGraph cfg) {
    this.rTypeFactory = typeFactory;

    this.cfg = cfg;
    OWNING = AnnotationBuilder.fromClass(this.rTypeFactory.getElementUtils(), Owning.class);
    MUSTCALLALIAS =
        AnnotationBuilder.fromClass(this.rTypeFactory.getElementUtils(), MustCallAlias.class);
    MUSTCALL =
        AnnotationBuilder.fromClass(this.rTypeFactory.getElementUtils(), InheritableMustCall.class);
  }

  private Set<Obligation> getNonEmptyMCParams(ControlFlowGraph cfg) {
    // TODO what about lambdas?
    if (cfg.getUnderlyingAST().getKind() != UnderlyingAST.Kind.METHOD) {
      return Collections.emptySet();
    }
    MethodTree method = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    Set<Obligation> result = new LinkedHashSet<>(1);
    for (int i = 0; i < method.getParameters().size(); i++) {
      VariableTree param = method.getParameters().get(i);
      VariableElement paramElement = TreeUtils.elementFromDeclaration(param);
      if (!rTypeFactory.hasMustCallAlias(paramElement)
          && paramElement.getAnnotation(Owning.class) == null) {
        if (rTypeFactory.declaredTypeHasMustCall(param)) {
          result.add(
              new Obligation(
                  ImmutableSet.of(new ResourceAlias(new LocalVariable(paramElement), param))));
        }
      } else {
        ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
        if (rTypeFactory.hasMustCallAlias(paramElement)) {
          addMustCallAlias(methodElt, i);
        } else if (paramElement.getAnnotation(Owning.class) != null) {
          addOwningOnParams(methodElt, i);
        }
      }
    }
    return result;
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
            checkMethodInvocation(obligations, (MethodInvocationNode) node);
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
      Node tempVar = getTempVarOrNode(arguments.get(i));
      if (!(tempVar instanceof LocalVariableNode)) {
        continue;
      }

      Obligation obligation = getObligationForVar(obligations, (LocalVariableNode) tempVar);
      if (obligation == null) {
        continue;
      }

      Node method = getTempVarOrNode(node);
      if (rTypeFactory.hasMustCallAlias(parameters.get(i))) {
        Set<ResourceAlias> newResourceAliasesForObligation =
            new LinkedHashSet<>(obligation.resourceAliases);
        newResourceAliasesForObligation.add(
            new ResourceAlias(new LocalVariable((LocalVariableNode) method), node.getTree()));
        obligations.remove(obligation);
        obligations.add(new Obligation(newResourceAliasesForObligation));
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
    if (!(returnNode instanceof LocalVariableNode)) {
      return;
    }
    Obligation returnObligation = getObligationForVar(obligations, (LocalVariableNode) returnNode);
    if (returnObligation == null) {
      return;
    }

    Set<ResourceAlias> returnAliases = returnObligation.resourceAliases;
    for (ResourceAlias rl : returnAliases) {
      Element returnElt = rl.reference.getElement();
      for (int i = 0; i < params.size(); i++) {
        VariableElement paramElt = TreeUtils.elementFromDeclaration(params.get(i));
        if (returnElt.equals(paramElt)) {
          ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
          addMustCallAlias(methodElt, i);
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

    if (lhsElement.getKind() == ElementKind.FIELD) {
      if (rTypeFactory.getDeclAnnotation(lhsElement, Owning.class) == null
          && !owningFields.contains(lhsElement)) {
        return;
      }

      owningFields.add((VariableElement) lhsElement);
      if (TreeUtils.isConstructor(method)) {
        if (!(rhs instanceof LocalVariableNode)) {
          return;
        }
        Obligation rhsObligation = getObligationForVar(obligations, (LocalVariableNode) rhs);
        if (rhsObligation == null) {
          return;
        }
        Set<ResourceAlias> rhsAliases = rhsObligation.resourceAliases;
        for (ResourceAlias rl : rhsAliases) {
          Element rhdElt = rl.reference.getElement();
          List<? extends VariableTree> params = method.getParameters();
          for (int i = 0; i < params.size(); i++) {
            VariableElement paramElt = TreeUtils.elementFromDeclaration(params.get(i));
            if (paramElt.equals(rhdElt)) {
              ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
              addMustCallAlias(methodElt, i);
              removeObligationsContainingVar(obligations, (LocalVariableNode) rhs);
              break;
            }
          }
        }
      } else if (!(rhs instanceof NullLiteralNode)) {
        AnnotationBuilder builder =
            new AnnotationBuilder(rTypeFactory.getProcessingEnv(), CreatesMustCallFor.class);
        builder.setValue("value", "this");
        AnnotationMirror am = builder.build();
        ExecutableElement methodElt = TreeUtils.elementFromDeclaration(method);
        WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
        wpi.addMethodDeclarationAnnotation(methodElt, am);
        if (owningFieldToECM.contains((VariableElement) lhsElement)) {
          owningFieldToECM.remove((VariableElement) lhsElement);
        }
      }

    } else if (lhs instanceof LocalVariableNode) {
      if (!(rhs instanceof LocalVariableNode)) {
        return;
      }
      Obligation rhsObligation = getObligationForVar(obligations, (LocalVariableNode) rhs);
      if (rhsObligation == null) {
        return;
      }
      LocalVariableNode lhsVar = (LocalVariableNode) lhs;
      Obligation lhsObligation = getObligationForVar(obligations, lhsVar);
      if (lhsObligation == null) {
        Set<ResourceAlias> newResourceAliasesForObligation =
            new LinkedHashSet<>(rhsObligation.resourceAliases);
        newResourceAliasesForObligation.add(
            new ResourceAlias(new LocalVariable(lhsVar), lhs.getTree()));
        obligations.remove(rhsObligation);
        obligations.add(new Obligation(newResourceAliasesForObligation));
      } else {
        // TODO killing assignment
      }
    }
  }

  private void addMustCallAlias(ExecutableElement methodElt, int index) {
    WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
    wpi.addMethodDeclarationAnnotation(methodElt, MUSTCALLALIAS);
    wpi.addDeclarationAnnotationToFormalParameter(methodElt, index, MUSTCALLALIAS);
  }

  private void addOwningOnParams(ExecutableElement methodElt, int index) {
    WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
    wpi.addDeclarationAnnotationToFormalParameter(methodElt, index, OWNING);
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
    Node temp = rTypeFactory.getTempVarForNode(node);
    if (temp != null) {
      return temp;
    }
    return node;
  }

  private void isOwningField(VariableElement receiverEl, MethodInvocationNode mNode) {
    if (receiverEl != null && rTypeFactory.isCandidateOwningField(receiverEl)) {
      Element method = TreeUtils.elementFromUse(mNode.getTree());
      List<String> mustCallValues = rTypeFactory.getMustCallValue(receiverEl);
      if (mustCallValues.size() == 1
          && mustCallValues.contains(method.getSimpleName().toString())) {
        // This assumes that any MustCall annotation has at most one element.
        // TODO: generalize this to MustCall annotations with more than one element.
        owningFields.add(receiverEl);
        owningFieldToECM.add(receiverEl);
      }
    } else {

    }
  }

  private void addEnsuresCalledMethods() {
    MethodTree enMethod = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    ExecutableElement enclosignMethodElt = TreeUtils.elementFromDeclaration(enMethod);
    String[] values = new String[owningFieldToECM.size()];
    String[] methods = new String[] {null};
    int index = 0;
    for (VariableElement ve : owningFieldToECM) {
      values[index] = "this." + ve.getSimpleName().toString();
      // we assume that if we have multiple fields they all have the same must call obligation
      methods[0] = rTypeFactory.getMustCallValue(ve).get(0);
    }
    AnnotationBuilder builder =
        new AnnotationBuilder(rTypeFactory.getProcessingEnv(), EnsuresCalledMethods.class);
    builder.setValue("value", values);
    builder.setValue("methods", methods);
    AnnotationMirror am = builder.build();
    WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
    wpi.addMethodDeclarationAnnotation(enclosignMethodElt, am);
  }

  private void addOrUpdateMustCall() {
    MethodTree enMethod = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    AnnotationBuilder builder =
        new AnnotationBuilder(rTypeFactory.getProcessingEnv(), InheritableMustCall.class);
    String[] methodArray = new String[] {enMethod.getName().toString()};
    Arrays.sort(methodArray);
    builder.setValue("value", methodArray);
    ClassTree classTree = TreePathUtil.enclosingClass(rTypeFactory.getPath(enMethod));
    TypeElement typeElement = TreeUtils.elementFromDeclaration(classTree);
    if (rTypeFactory.getMustCallValue(typeElement).isEmpty()) {
      WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
      wpi.addClassDeclarationAnnotation(typeElement, builder.build());
    }
    //    else {
    //      builder = new AnnotationBuilder(rTypeFactory.getProcessingEnv(), MustCall.class);
    //      builder.setValue("value", rTypeFactory.getMustCallValue(typeElement));
    //      WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
    //      wpi.addClassDeclarationAnnotation(typeElement, builder.build());
    //    }
  }

  private void checkArgsOfMethodCall(
      Set<Obligation> obligations,
      ExecutableElement methodElt,
      MethodInvocationNode mNode,
      List<? extends VariableTree> paramsOfEnclosingMethod) {
    List<Node> arguments = getArgumentsOfInvocation(mNode);
    List<? extends VariableElement> parameters = getParametersOfInvocation(mNode);
    if (parameters.isEmpty()) {
      return;
    }

    for (int i = 0; i < arguments.size(); i++) {
      Node arg = NodeUtils.removeCasts(arguments.get(i));
      Element argElt = TreeUtils.elementFromTree(arg.getTree());
      if (argElt == null && arg instanceof ArrayCreationNode) {
        ArrayCreationNode arrayCreationNode = (ArrayCreationNode) arg;
        List<Node> arrays = arrayCreationNode.getInitializers();
        for (Node n : arrays) {
          Element nElt = TreeUtils.elementFromTree(n.getTree());
          if (nElt == null || !rTypeFactory.isCandidateOwningField(nElt)) {
            continue;
          }
          CFStore cmStoreAfter = rTypeFactory.getStoreAfter(mNode);
          JavaExpression target = JavaExpression.fromNode(n);
          CFValue cmValue = cmStoreAfter == null ? null : cmStoreAfter.getValue(target);

          AnnotationMirror cmAnno = null;
          if (cmValue != null) {
            for (AnnotationMirror anno : cmValue.getAnnotations()) {
              if (AnnotationUtils.areSameByName(
                  anno, "org.checkerframework.checker.calledmethods.qual.CalledMethods")) {
                cmAnno = anno;
                break;
              }
            }
          }

          if (cmAnno == null) {
            cmAnno = rTypeFactory.top;
          }

          List<String> mustCallValues = rTypeFactory.getMustCallValue(nElt);

          AnnotationMirror cmAnnoForMustCallMethods =
              rTypeFactory.createCalledMethods(mustCallValues.toArray(new String[0]));
          if (rTypeFactory.getQualifierHierarchy().isSubtype(cmAnno, cmAnnoForMustCallMethods)) {
            owningFields.add((VariableElement) nElt);
            owningFieldToECM.add((VariableElement) nElt);
            // TODO infer @MustCall and @EnsuresCalledMethods
          }
        }
      }

      if (argElt != null && rTypeFactory.isCandidateOwningField(argElt)) {
        CFStore cmStoreAfter = rTypeFactory.getStoreAfter(mNode);
        JavaExpression target = JavaExpression.fromNode(arg);
        CFValue cmValue = cmStoreAfter == null ? null : cmStoreAfter.getValue(target);

        AnnotationMirror cmAnno = null;
        if (cmValue != null) {
          for (AnnotationMirror anno : cmValue.getAnnotations()) {
            if (AnnotationUtils.areSameByName(
                anno, "org.checkerframework.checker.calledmethods.qual.CalledMethods")) {
              cmAnno = anno;
              break;
            }
          }
        }

        if (cmAnno == null) {
          cmAnno = rTypeFactory.top;
        }

        List<String> mustCallValues = rTypeFactory.getMustCallValue(argElt);

        AnnotationMirror cmAnnoForMustCallMethods =
            rTypeFactory.createCalledMethods(mustCallValues.toArray(new String[0]));
        if (rTypeFactory.getQualifierHierarchy().isSubtype(cmAnno, cmAnnoForMustCallMethods)) {
          owningFields.add((VariableElement) argElt);
          owningFieldToECM.add((VariableElement) argElt);
          // TODO infer @MustCall and @EnsuresCalledMethods
        }
      }

      if (!rTypeFactory.hasMustCallAlias(parameters.get(i))
          && !rTypeFactory.hasOwning(parameters.get(i))) {
        continue;
      }

      Node tempVar = getTempVarOrNode(arg);
      if (!(tempVar instanceof LocalVariableNode)) {
        continue;
      }

      Obligation argumentObligation = getObligationForVar(obligations, (LocalVariableNode) tempVar);
      if (argumentObligation == null) {
        continue;
      }

      Set<ResourceAlias> argAliases = argumentObligation.resourceAliases;
      for (ResourceAlias rl : argAliases) {
        Element argAliasElt = rl.reference.getElement();
        for (int j = 0; j < paramsOfEnclosingMethod.size(); j++) {
          VariableElement paramElt =
              TreeUtils.elementFromDeclaration(paramsOfEnclosingMethod.get(j));
          if (!argAliasElt.equals(paramElt)) {
            continue;
          }
          if (TreeUtils.isSuperConstructorCall(mNode.getTree())
              || TreeUtils.isThisConstructorCall(mNode.getTree())) {
            if (rTypeFactory.hasMustCallAlias(parameters.get(i))) {
              addMustCallAlias(methodElt, j);
            }
          } else if (rTypeFactory.hasOwning(parameters.get(i))) {
            addOwningOnParams(methodElt, j);
          }
        }
      }
    }
  }

  private void checkReceiverOfMethodCall(
      Set<Obligation> obligations,
      ExecutableElement methodElt,
      MethodInvocationNode mNode,
      List<? extends VariableTree> paramsOfEnclosingMethod) {
    List<String> mustCallValues;
    Node receiver = mNode.getTarget().getReceiver();
    if (receiver.getTree() == null) {
      return;
    }

    Element receiverEl = TreeUtils.elementFromTree(receiver.getTree());
    if (receiverEl != null && rTypeFactory.isCandidateOwningField(receiverEl)) {
      isOwningField((VariableElement) receiverEl, mNode);
    }

    Node receiverTmpVar = getTempVarOrNode(receiver);
    if (!(receiverTmpVar instanceof LocalVariableNode)) {
      return;
    }

    Obligation receiverObligation =
        getObligationForVar(obligations, (LocalVariableNode) receiverTmpVar);
    if (receiverObligation == null) {
      return;
    }

    Set<ResourceAlias> receiverAliases = receiverObligation.resourceAliases;
    for (ResourceAlias rl : receiverAliases) {
      Element resourceElt = rl.reference.getElement();

      for (int i = 0; i < paramsOfEnclosingMethod.size(); i++) {
        VariableElement paramElt = TreeUtils.elementFromDeclaration(paramsOfEnclosingMethod.get(i));
        if (!resourceElt.equals(paramElt)) {
          continue;
        }

        mustCallValues = rTypeFactory.getMustCallValue(paramElt);
        if (mustCallValues.size() > 1 || mustCallValues.size() == 0) {
          // TODO: generalize this to MustCall annotations with more than one element.
          return;
        }

        CFStore cmStoreAfter = rTypeFactory.getStoreAfter(mNode);
        JavaExpression target = JavaExpression.fromVariableTree(paramsOfEnclosingMethod.get(i));
        CFValue cmValue = cmStoreAfter == null ? null : cmStoreAfter.getValue(target);

        AnnotationMirror cmAnno = null;
        if (cmValue != null) {
          for (AnnotationMirror anno : cmValue.getAnnotations()) {
            if (AnnotationUtils.areSameByName(
                anno, "org.checkerframework.checker.calledmethods.qual.CalledMethods")) {
              cmAnno = anno;
              break;
            }
          }
        }
        if (cmAnno == null) {
          cmAnno = rTypeFactory.top;
        }

        AnnotationMirror cmAnnoForMustCallMethods =
            rTypeFactory.createCalledMethods(mustCallValues.toArray(new String[0]));
        if (rTypeFactory.getQualifierHierarchy().isSubtype(cmAnno, cmAnnoForMustCallMethods)) {
          addOwningOnParams(methodElt, i);
        }
      }
    }
  }

  private void checkIndirectCalls(
      ExecutableElement methodElt,
      MethodInvocationNode mNode,
      List<? extends VariableTree> paramsOfEnclosingMethod) {
    List<String> mustCallValues;
    for (int i = 0; i < paramsOfEnclosingMethod.size(); i++) {
      VariableElement paramElt = TreeUtils.elementFromDeclaration(paramsOfEnclosingMethod.get(i));
      mustCallValues = rTypeFactory.getMustCallValue(paramElt);
      if (mustCallValues.size() > 1 || mustCallValues.size() == 0) {
        // TODO: generalize this to MustCall annotations with more than one element.
        return;
      }

      CFStore cmStoreAfter = rTypeFactory.getStoreAfter(mNode);
      JavaExpression target = JavaExpression.fromVariableTree(paramsOfEnclosingMethod.get(i));
      CFValue cmValue = cmStoreAfter == null ? null : cmStoreAfter.getValue(target);

      AnnotationMirror cmAnno = null;
      if (cmValue != null) {
        for (AnnotationMirror anno : cmValue.getAnnotations()) {
          if (AnnotationUtils.areSameByName(
              anno, "org.checkerframework.checker.calledmethods.qual.CalledMethods")) {
            cmAnno = anno;
            break;
          }
        }
      }

      if (cmAnno == null) {
        cmAnno = rTypeFactory.top;
      }

      AnnotationMirror cmAnnoForMustCallMethods =
          rTypeFactory.createCalledMethods(mustCallValues.toArray(new String[0]));
      if (rTypeFactory.getQualifierHierarchy().isSubtype(cmAnno, cmAnnoForMustCallMethods)) {
        addOwningOnParams(methodElt, i);
      }
    }
  }

  /**
   * If the receiver of {@code mNode} is a candidate owning field and the method invocation
   * satisfies the field's must-call obligation, then adds that field to the {@link #owningFields}
   * set.
   *
   * @param mNode the MethodInvocationNode
   */
  private void checkMethodInvocation(Set<Obligation> obligations, MethodInvocationNode mNode) {
    MethodTree enMethod = ((UnderlyingAST.CFGMethod) cfg.getUnderlyingAST()).getMethod();
    ExecutableElement methodElt = TreeUtils.elementFromDeclaration(enMethod);
    if (methodElt == null) {
      return;
    }

    if (rTypeFactory.hasCreatesMustCallFor(mNode)) {
      AnnotationBuilder builder =
          new AnnotationBuilder(rTypeFactory.getProcessingEnv(), CreatesMustCallFor.class);
      builder.setValue("value", "this");
      AnnotationMirror am = builder.build();
      WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();
      wpi.addMethodDeclarationAnnotation(methodElt, am);
    }

    List<? extends VariableTree> paramsOfEnclosingMethod = enMethod.getParameters();

    checkArgsOfMethodCall(obligations, methodElt, mNode, paramsOfEnclosingMethod);
    checkReceiverOfMethodCall(obligations, methodElt, mNode, paramsOfEnclosingMethod);
    checkIndirectCalls(methodElt, mNode, paramsOfEnclosingMethod);
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
  private void propagateRegPaths(
      Set<Obligation> obligations,
      Block curBlock,
      Set<BlockWithObligations> visited,
      Deque<BlockWithObligations> worklist) {

    List<Block> successors = getNormalSuccessors(curBlock);

    for (Block successor : successors) {
      // If b is a special block, it must be the regular exit, since we do not propagate to
      // exceptional successors.
      if (successor.getType() == Block.BlockType.SPECIAL_BLOCK) {
        WholeProgramInference wpi = rTypeFactory.getWholeProgramInference();

        assert wpi != null : "MustCallInference is running without WPI.";
        for (VariableElement fieldElt : owningFields) {
          wpi.addFieldDeclarationAnnotation(fieldElt, OWNING);
        }
        if (!owningFieldToECM.isEmpty()) {
          addEnsuresCalledMethods(); // addEnsuresCalledMethods(receiverEl, methodElt, method);
        }

        if (!owningFields.isEmpty() && owningFields.size() == owningFieldToECM.size()) {
          addOrUpdateMustCall();
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
