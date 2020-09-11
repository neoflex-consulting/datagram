package MetaServer.sse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class DatasetBuildTest {
    private abstract static class AbstractDataset {
        final String name;

        AbstractDataset(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", AbstractDataset.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .toString();
        }

        public abstract void build(DsBuilder builder);
    }

    private static class Dataset extends AbstractDataset {
        private final List<AbstractDataset> datasets;

        Dataset(String name, List<AbstractDataset> datasets) {
            super(name);
            this.datasets = datasets;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Dataset.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("datasets=" + datasets)
                    .toString();
        }

        @Override
        public void build(DsBuilder builder) {
            if (builder.isFullRebuild || this == builder.root) {
                for (AbstractDataset ds: datasets) {
                    ds.build(builder);
                }
                builder.visitBuild(this);
            }
            builder.visitRegister(this, this.name);
        }
    }

    private static class LinkedDataset extends AbstractDataset {
        private final AbstractDataset linkTo;

        LinkedDataset(String name, AbstractDataset linkTo) {
            super(name);
            this.linkTo = linkTo;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", LinkedDataset.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("linkTo=" + linkTo)
                    .toString();
        }

        @Override
        public void build(DsBuilder builder) {
            AbstractDataset link = this.linkTo;
            while (link instanceof LinkedDataset) {
                link = ((LinkedDataset)link).linkTo;
            }
            builder.visitRegister(link, this.name);
        }
    }

    private static class TableDataset extends AbstractDataset {

        TableDataset(String name) {
            super(name);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", TableDataset.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .toString();
        }

        @Override
        public void build(DsBuilder builder) {
            builder.visitRegister(this, this.name);
        }
    }

    private static class DsBuilder {
        private final List<String> buildOrder = new ArrayList<>();
        boolean isFullRebuild = false;
        AbstractDataset root = null;

        List<String> build(AbstractDataset root, boolean isFullRebuild) {
            this.isFullRebuild = isFullRebuild;
            this.root = root;
            buildOrder.clear();
            root.build(this);
            return buildOrder;
        }

        void visitBuild(AbstractDataset ds) {
            buildOrder.add("build:" + ds.name);
        }

        void visitRegister(AbstractDataset ds, String registerName) {
            buildOrder.add("register:" + ds.name + " as: " + registerName);
        }
    }

    private AbstractDataset testDs;

    @Before
    public void setUp() {
        AbstractDataset tab1 = new TableDataset("tab1");
        AbstractDataset tab2 = new TableDataset("tab2");
        AbstractDataset tab3 = new TableDataset("tab3");
        AbstractDataset tab4 = new TableDataset("tab4");
        AbstractDataset link1 = new LinkedDataset("link1", tab1);
        AbstractDataset link2 = new LinkedDataset("link2", link1);
        AbstractDataset ds1 = new Dataset("ds1", Arrays.asList(tab2, tab3, link2));
        testDs = new Dataset("testDs", Arrays.asList(tab1, tab4, ds1));
    }

    @Test
    public void testTreeBuild() {
        List<String> build = new DsBuilder().build(testDs, true);
        Assert.assertEquals(9, build.size());

        build = new DsBuilder().build(testDs, false);
        Assert.assertEquals(5, build.size());

    }
}
