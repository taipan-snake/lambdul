public class AstAbstraction extends AstExpression {
    private AstVariable variable;
    private AstExpression body;

    public AstAbstraction(AstVariable variable, AstExpression body) {
        this.variable = variable;
        this.body = body;
    }

    /**
     * Get the argument of the abstraction.
     *
     * @return  the argument variable
     */
    public AstVariable getVariable() {
        // TODO: consider changing to getArgument()
        return this.variable;
    }

    /**
     * Get the body expression of the abstraction.
     *
     * @return  the expression in the body
     */
    public AstExpression getBody() {
        return this.body;
    }

    @Override
    public boolean usesFreeVariable(AstEnvironment env, AstVariable var) {
        if (this.variable.equals(var)) {
            // Variable is bound in the body, so no longer free
            return false;
        } else {
            // Variable is still free; check the body
            return this.body.usesFreeVariable(env, var);
        }
    }

    @Override
    public AstExpression evaluate(AstEnvironment env) {
        if (env.isBound(this.variable.getName())) {
            // Variable already being used - perform an alpha-reduction

            // Get a new unused variable name to use
            AstVariable newVariable = env.renameVariable(this.variable.getName());

            // Replace all body variables bound to this occurrence with the new name
            AstExpression newBody = this.body.substitute(this.variable, newVariable, env).evaluate(env);

            // Evaluate the alpha-reduced expression instead
            AstExpression reducedExpression = new AstAbstraction(newVariable, newBody);
            AstExpression result = reducedExpression.evaluate(env);

            return result;
        }

        /* Check if an eta-reduction applies:
         *      \x.(f x) <=> f
         * Note that:
         *      -> The body must be an application where the RHS is the abstraction's argument
         *      -> f must be independent of x
         */
        if (this.body instanceof AstApplication) {
            AstApplication application = (AstApplication) this.body;
            if (application.getRight() instanceof AstVariable) {
                AstVariable subvariable = (AstVariable) application.getRight();
                if (subvariable.equals(this.variable)) {
                    // Check if f is independent of x
                    // f is independent of x <=> x is not a free variable in f
                    if (!application.getLeft().usesFreeVariable(env, this.variable)) {
                        // eta-application applies!
                        // Abstraction is of the form \x.(f x), so just evaluate f
                        return application.getLeft().evaluate(env);
                    }
                }
            }
        }

        // Bind the variable before evaluating
        env.bindVariable(this.variable.getName());

        // Evaluate the body
        AstExpression evaluatedBody = this.body.evaluate(env);

        // Free the variable - no longer being used
        env.freeVariable(this.variable.getName());

        return new AstAbstraction(this.variable.clone(), evaluatedBody);
    }

    @Override
    public AstExpression substitute(AstVariable var, AstExpression expr, AstEnvironment env) {
        if (var.equals(this)) {
            // Variable names overlap - don't go any further
            return this.clone();
        } else if (expr instanceof AstVariable && ((AstVariable) expr).equals(this.variable)) {
            // Name clash! Need to avoid this.
            // e.g. (\x.(\y.x) y) = \x.(\y'.y) - NOT \x.(\y.y)
            AstVariable replacementVariable = (AstVariable) expr;

            // Get a new variable to replace the head variable
            env.bindVariable(replacementVariable.getName());
            AstVariable newVariable = env.renameVariable(replacementVariable.getName());
            env.freeVariable(replacementVariable.getName());

            // Rename its occurrences in the body
            AstExpression renamedBody = this.body.substitute(replacementVariable, newVariable, env);

            // Create the newly substituted abstraction
            AstAbstraction renamedAbstraction = new AstAbstraction(newVariable, renamedBody);

            // Continue the substitution we actually wanted
            return renamedAbstraction.substitute(var, expr, env);
        } else {
            // Bindings do not change otherwise, so just substitute in the body
            AstExpression substitutedBody = this.body.substitute(var, expr, env).evaluate(env);
            return new AstAbstraction(this.variable.clone(), substitutedBody);
        }
    }


    @Override
    public String toString() {
        return "λ" + variable.toString() + ".(" + body.toString() + ")";
    }

    @Override
    public AstAbstraction clone() {
        return new AstAbstraction(this.variable.clone(), this.body.clone());
    }
}