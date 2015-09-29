package spoon.test.imports;

import org.junit.Test;
import spoon.Launcher;
import spoon.compiler.SpoonCompiler;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.ImportScanner;
import spoon.reflect.visitor.ImportScannerImpl;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.NameFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.test.imports.testclasses.ClassWithInvocation;
import spoon.test.imports.testclasses.ClientClass;
import spoon.test.imports.testclasses.SubClass;
import spoon.test.imports.testclasses.internal.ChildClass;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ImportTest {

	@Test
	public void testImportOfAnInnerClassInASuperClassPackage() throws Exception {
		Launcher spoon = new Launcher();
		Factory factory = spoon.createFactory();

		SpoonCompiler compiler = spoon.createCompiler(
				factory,
				SpoonResourceHelper.resources(
						"./src/test/java/spoon/test/imports/testclasses/internal/SuperClass.java",
						"./src/test/java/spoon/test/imports/testclasses/internal/ChildClass.java",
						"./src/test/java/spoon/test/imports/testclasses/ClientClass.java"));

		compiler.build();

		final List<CtClass<?>> classes = Query.getElements(factory, new NameFilter<CtClass<?>>("ClientClass"));

		final CtClass<?> innerClass = classes.get(0).getNestedType("InnerClass");
		String expected = "spoon.test.imports.testclasses.ClientClass.InnerClass";
		assertEquals(expected, innerClass.getReference().toString());

		expected = "spoon.test.imports.testclasses.internal.ChildClass.InnerClassProtected";
		assertEquals(expected, innerClass.getSuperclass().toString());
	}

	@Test
	public void testImportOfAnInnerClassInASuperClassAvailableInLibrary() throws Exception {
		SpoonCompiler comp = new Launcher().createCompiler();
		List<SpoonResource> fileToBeSpooned = SpoonResourceHelper.resources("./src/test/resources/visibility/YamlRepresenter.java");
		assertEquals(1, fileToBeSpooned.size());
		comp.addInputSources(fileToBeSpooned);
		List<SpoonResource> classpath = SpoonResourceHelper.resources("./src/test/resources/visibility/snakeyaml-1.9.jar");
		assertEquals(1, classpath.size());
		comp.setSourceClasspath(classpath.get(0).getPath());
		comp.build();
		Factory factory = comp.getFactory();
		CtType<?> theClass = factory.Type().get("visibility.YamlRepresenter");

		final CtClass<?> innerClass = theClass.getNestedType("RepresentConfigurationSection");
		String expected = "visibility.YamlRepresenter.RepresentConfigurationSection";
		assertEquals(expected, innerClass.getReference().toString());

		expected = "org.yaml.snakeyaml.representer.Representer.RepresentMap";
		assertEquals(expected, innerClass.getSuperclass().toString());
	}

	@Test
	public void testImportOfAnInnerClassInAClassPackage() throws Exception {
		Launcher spoon = new Launcher();
		Factory factory = spoon.createFactory();

		SpoonCompiler compiler = spoon.createCompiler(
				factory,
				SpoonResourceHelper.resources(
						"./src/test/java/spoon/test/imports/testclasses/internal/PublicSuperClass.java",
						"./src/test/java/spoon/test/imports/testclasses/DefaultClientClass.java"));

		compiler.build();

		final CtClass<?> client = (CtClass<?>) factory.Type().get("spoon.test.imports.testclasses.DefaultClientClass");
		final CtMethod<?> methodVisit = client.getMethodsByName("visit").get(0);

		final CtType<Object> innerClass = factory.Type().get("spoon.test.imports.testclasses.DefaultClientClass$InnerClass");
		assertEquals("Type of the method must to be InnerClass accessed via DefaultClientClass.", innerClass, methodVisit.getType().getDeclaration());
	}

	@Test
	public void testNewInnerClassDefinesInItsClassAndSuperClass() throws Exception {
		Launcher spoon = new Launcher();
		Factory factory = spoon.createFactory();

		SpoonCompiler compiler = spoon.createCompiler(
				factory,
				SpoonResourceHelper.resources(
						"./src/test/java/spoon/test/imports/testclasses/SuperClass.java",
						"./src/test/java/spoon/test/imports/testclasses/SubClass.java"));

		compiler.build();
		final CtClass<?> subClass = (CtClass<?>) factory.Type().get(SubClass.class);
		final CtConstructorCall<?> ctConstructorCall = subClass.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class)).get(
				0);

		assertEquals("new spoon.test.imports.testclasses.SubClass.Item(\"\")",
					 ctConstructorCall.toString());
		final String expected = "public class SubClass extends spoon.test.imports.testclasses.SuperClass {" + System.lineSeparator()
				+ "    public void aMethod() {" + System.lineSeparator()
				+ "        new spoon.test.imports.testclasses.SubClass.Item(\"\");" + System.lineSeparator()
				+ "    }" + System.lineSeparator()
				+ System.lineSeparator()
				+ "    public static class Item extends spoon.test.imports.testclasses.SuperClass.Item {" + System.lineSeparator()
				+ "        public Item(java.lang.String s) {" + System.lineSeparator()
				+ "            super(1, s);" + System.lineSeparator()
				+ "        }" + System.lineSeparator()
				+ "    }" + System.lineSeparator()
				+ "}";
		assertEquals(expected, subClass.toString());
	}

	@Test
	public void testMissingImport() throws Exception {
		Launcher spoon = new Launcher();
		Factory factory = spoon.createFactory();
		factory.getEnvironment().setNoClasspath(true);
		factory.getEnvironment().setLevel("OFF");

		SpoonCompiler compiler = spoon.createCompiler(
				factory,
				SpoonResourceHelper.resources("./src/test/resources/import-resources/fr/inria/MissingImport.java"));

		compiler.build();
		CtTypeReference<?> type = factory.Class().getAll().get(0).getFields().get(0).getType();
		assertEquals("Abcd", type.getSimpleName());
		assertEquals("fr.inria.internal", type.getPackage().getSimpleName());
	}

	@Test
	public void testSpoonWithImports() throws Exception {
		final Launcher launcher = new Launcher();
		launcher.run(new String[] {
				"-i", "./src/test/java/spoon/test/imports/testclasses",
				"-o", "./target/spooned",
				"--with-imports"
		});
		final CtClass<ImportTest> aClass = launcher.getFactory().Class().get(ChildClass.class);
		final CtClass<ImportTest> anotherClass = launcher.getFactory().Class().get(ClientClass.class);
		final CtClass<ImportTest> classWithInvocation = launcher.getFactory().Class().get(ClassWithInvocation.class);

		final ImportScanner importScanner = new ImportScannerImpl();
		final Collection<CtTypeReference<?>> imports = importScanner.computeImports(aClass);
		assertEquals(2, imports.size());
		final Collection<CtTypeReference<?>> imports1 = importScanner.computeImports(anotherClass);
		assertEquals(1, imports1.size());
		final Collection<CtTypeReference<?>> imports2 = importScanner.computeImports(classWithInvocation);
		assertEquals("Spoon ignores the arguments of CtInvocations", 1, imports2.size());
	}
}
