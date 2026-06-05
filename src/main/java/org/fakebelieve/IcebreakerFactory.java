package org.fakebelieve;

import picocli.CommandLine;

public class IcebreakerFactory implements CommandLine.IFactory {
    private final IcebreakerContext context;
    private final CommandLine.IFactory defaultFactory = CommandLine.defaultFactory();

    public IcebreakerFactory(IcebreakerContext context) {
        this.context = context;
    }

    @Override
    public <K> K create(Class<K> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor(IcebreakerContext.class).newInstance(context);
        } catch (NoSuchMethodException e) {
            return defaultFactory.create(clazz);
        }
    }
}
