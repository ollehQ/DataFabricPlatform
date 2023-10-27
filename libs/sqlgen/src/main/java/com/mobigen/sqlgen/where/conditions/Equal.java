package com.mobigen.sqlgen.where.conditions;

import com.mobigen.sqlgen.where.AbstractBinaryCondition;

/**
 * <p>
 * Created by fwani.
 *
 * @version 0.0.1
 * @since 0.0.1
 */
public class Equal<L, R> extends AbstractBinaryCondition<L, R> {
    private Equal(L left, R right) {
        super(left, right);
    }

    @Override
    public String operator() {
        return "=";
    }

    public static <L, R> Equal<L, R> of(L left, R right) {
        return new Equal<>(left, right);
    }
}
