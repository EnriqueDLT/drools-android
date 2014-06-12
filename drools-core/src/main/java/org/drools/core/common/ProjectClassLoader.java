package org.drools.core.common;

import org.drools.core.util.ClassUtils;
import org.kie.internal.utils.ClassLoaderUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.drools.core.util.ClassUtils.convertClassToResourcePath;

public class ProjectClassLoader extends ClassLoader {

    private static final boolean CACHE_NON_EXISTING_CLASSES = true;
    private static final ClassNotFoundException dummyCFNE = CACHE_NON_EXISTING_CLASSES ?
                                                            new ClassNotFoundException("This is just a cached Exception. Disable non existing classes cache to see the actual one.") :
                                                            null;

    private Map<String, byte[]> store;

    private Map<String, ClassBytecode> definedTypes;

    private final Set<String> nonExistingClasses = new HashSet<String>();

    private ClassLoader droolsClassLoader;

    private InternalTypesClassLoader typesClassLoader;

    private ProjectClassLoader(ClassLoader parent) {
        super(parent);
    }

    public static ClassLoader getClassLoader(final ClassLoader[] classLoaders,
                                             final Class< ? > cls,
                                             final boolean enableCache) {
        if (classLoaders == null || classLoaders.length == 0) {
            return cls == null ? createProjectClassLoader() : createProjectClassLoader(cls.getClassLoader());
        } else if (classLoaders.length == 1) {
            ProjectClassLoader classLoader = createProjectClassLoader(classLoaders[0]);
            if (cls != null) {
                classLoader.setDroolsClassLoader(cls.getClassLoader());
            }
            return classLoader;
        } else {
            return ClassLoaderUtil.getClassLoader(classLoaders, cls, enableCache);
        }
    }

    public static ProjectClassLoader createProjectClassLoader() {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        if (parent == null) {
            parent = ProjectClassLoader.class.getClassLoader();
        }
        return new ProjectClassLoader(parent);
    }

    public static ProjectClassLoader createProjectClassLoader(ClassLoader parent) {
        if (parent == null) {
            return createProjectClassLoader();
        }
        return parent instanceof ProjectClassLoader ? (ProjectClassLoader)parent : new ProjectClassLoader(parent);
    }

    public static ProjectClassLoader createProjectClassLoader(ClassLoader parent, Map<String, byte[]> store) {
        ProjectClassLoader projectClassLoader = createProjectClassLoader(parent);
        projectClassLoader.store = store;
        return projectClassLoader;
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return internalLoadClass(name, resolve);
        } catch (ClassNotFoundException e2) {
            return loadType(name, resolve);
        }
    }

    Class<?> internalLoadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (CACHE_NON_EXISTING_CLASSES && nonExistingClasses.contains(name)) {
            throw dummyCFNE;
        }

        if (droolsClassLoader != null) {
            try {
                return Class.forName(name, resolve, droolsClassLoader);
            } catch (ClassNotFoundException e) { }
        }
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            return Class.forName(name, resolve, getParent());
        }
    }

    private Class<?> loadType(String name, boolean resolve) throws ClassNotFoundException {
        ClassNotFoundException cnfe = null;
        if (typesClassLoader != null) {
            try {
                return typesClassLoader.loadType(name, resolve);
            } catch (ClassNotFoundException e) {
                cnfe = e;
            }
        }
        return tryDefineType(name, cnfe);
    }

    Class<?> tryDefineType(String name, ClassNotFoundException cnfe) throws ClassNotFoundException {
        byte[] bytecode = getBytecode(convertClassToResourcePath(name));
        if (bytecode == null) {
            if (CACHE_NON_EXISTING_CLASSES) {
                nonExistingClasses.add(name);
            }
            throw cnfe != null ? cnfe : new ClassNotFoundException(name);
        }
        return defineType(name, bytecode);
    }

    private Class<?> defineType(String name, byte[] bytecode) {
        if (definedTypes == null) {
            definedTypes = new HashMap<String, ClassBytecode>();
        } else {
            ClassBytecode existingClass = definedTypes.get(name);
            if (existingClass != null && Arrays.equals(bytecode, existingClass.bytes)) {
                return existingClass.clazz;
            }
        }

        if (typesClassLoader == null) {
            typesClassLoader = makeClassLoader();
        }
        Class<?> clazz = typesClassLoader.defineClass(name, bytecode);
        definedTypes.put(name, new ClassBytecode(clazz, bytecode));
        return clazz;
    }

    public Class<?> defineClass(String name, byte[] bytecode) {
        return defineClass(name, convertClassToResourcePath(name), bytecode);
    }

    public Class<?> defineClass(String name, String resourceName, byte[] bytecode) {
        storeClass(name, resourceName, bytecode);
        return defineType(name, bytecode);
    }

    public void storeClass(String name, String resourceName, byte[] bytecode) {
        if (store == null) {
            store = new HashMap<String, byte[]>();
        }
        store.put(resourceName, bytecode);
        if (CACHE_NON_EXISTING_CLASSES) {
            nonExistingClasses.remove(name);
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return getParent().getResources(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] bytecode = getBytecode(name);
        return bytecode != null ? new ByteArrayInputStream( bytecode ) : super.getResourceAsStream(name);
    }

    @Override
    public URL getResource(String name) {
        if (droolsClassLoader != null) {
            URL resource = droolsClassLoader.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return super.getResource(name);
    }

    public byte[] getBytecode(String resourceName) {
        return store == null ? null : store.get(resourceName);
    }

    public Map<String, byte[]> getStore() {
        return store;
    }

    public void setDroolsClassLoader(ClassLoader droolsClassLoader) {
        if (getParent() != droolsClassLoader) {
            this.droolsClassLoader = droolsClassLoader;
            if (CACHE_NON_EXISTING_CLASSES) {
                nonExistingClasses.clear();
            }
        }
    }

    public void initFrom(ProjectClassLoader other) {
        if (other.store != null) {
            if (store == null) {
                store = new HashMap<String, byte[]>();
            }
            store.putAll(other.store);
        }
        nonExistingClasses.addAll(other.nonExistingClasses);
    }

    private InternalTypesClassLoader makeClassLoader() {
        return ClassUtils.isAndroid() ?
                (InternalTypesClassLoader) ClassUtils.instantiateObject(
                        "org.drools.core.common.DexInternalTypesClassLoader", null, this) :
                new DefaultInternalTypesClassLoader( this );
    }

    interface InternalTypesClassLoader {
        Class<?> defineClass(String name, byte[] bytecode);
        Class<?> loadType(String name, boolean resolve) throws ClassNotFoundException;
    }

    private static class DefaultInternalTypesClassLoader extends ClassLoader implements InternalTypesClassLoader {

        private final ProjectClassLoader projectClassLoader;

        private DefaultInternalTypesClassLoader(ProjectClassLoader projectClassLoader) {
            this.projectClassLoader = projectClassLoader;
        }

        public Class<?> defineClass(String name, byte[] bytecode) {
            int lastDot = name.lastIndexOf( '.' );
            if (lastDot > 0) {
                String pkgName = name.substring( 0, lastDot );
                if (getPackage( pkgName ) == null) {
                    definePackage( pkgName, "", "", "", "", "", "", null );
                }
            }
            return super.defineClass(name, bytecode, 0, bytecode.length);
        }

        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try {
                return loadType(name, resolve);
            } catch (ClassNotFoundException cnfe) {
                synchronized(projectClassLoader) {
                    try {
                        return projectClassLoader.internalLoadClass(name, resolve);
                    } catch (ClassNotFoundException cnfe2) {
                        return projectClassLoader.tryDefineType(name, cnfe);
                    }
                }
            }
        }

        public Class<?> loadType(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }

    public synchronized void reinitTypes() {
        typesClassLoader = null;
        nonExistingClasses.clear();
    }

    private static class ClassBytecode {
        private final Class<?> clazz;
        private final byte[] bytes;

        private ClassBytecode(Class<?> clazz, byte[] bytes) {
            this.clazz = clazz;
            this.bytes = bytes;
        }
    }
}
