/**
 *  Copyright 2017 Gunnar Morling (http://www.gunnarmorling.de/)
 *  and/or other contributors as indicated by the @authors tag. See the
 *  copyright.txt file in the distribution for a full listing of all
 *  contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.gunnarmorling.jdkapidiff;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Optional;
import java.util.spi.ToolProvider;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class ModuleRepackager {

    public static class Args {

        @Parameter(names="--java8home")
        private File java8home;

        @Parameter(names="--java9home")
        private File java9home;

        @Parameter(names="--working-dir")
        private File workingDir;
    }

    public static void main(String[] argv) throws Exception {
        Args args = new Args();
        JCommander.newBuilder()
            .acceptUnknownOptions( true )
            .addObject( args )
            .build()
            .parse( argv );

        Path extractedClassesDir = args.workingDir.toPath().resolve( "extracted-classes" );
        delete( extractedClassesDir );

        mergeJava8Api( args.java8home.toPath(), extractedClassesDir );
        mergeJava9Api( args.java9home.toPath(), extractedClassesDir );
    }

    private static void mergeJava8Api(Path java8Home, Path extractedClassesDir) throws IOException {
        System.out.println( "Merging Java 8 Jars" );

        Path java8Dir = extractedClassesDir.resolve( "java8" );

        Optional<ToolProvider> jar = ToolProvider.findFirst( "jar" );
        if ( !jar.isPresent() ) {
            throw new IllegalStateException( "Couldn't find jar tool" );
        }

        Files.createDirectories( java8Dir );

        // Using separate process for using specific target directory
        Path rtJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "rt.jar" );
        System.out.println( "Extracting rt.jar" );
        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", rtJar.toString() ), java8Dir );

        Path javawsJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "javaws.jar" );
        System.out.println( "Extracting javaws.jar" );
        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", javawsJar.toString() ), java8Dir );

        Path jfxrtJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "ext" ).resolve( "jfxrt.jar" );
        System.out.println( "Extracting jfxrt.jar" );
        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", jfxrtJar.toString() ), java8Dir );

        Path nashornJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "ext" ).resolve( "nashorn.jar" );
        System.out.println( "Extracting nashorn.jar" );
        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", nashornJar.toString() ), java8Dir );

//        Path jceJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "jce.jar" );
//        System.out.println( "Extracting jce.jar" );
//        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", jceJar.toString() ), java8Dir );
//
//        Path jfrJar = java8Home.resolve( "jre" ).resolve( "lib" ).resolve( "jfr.jar" );
//        System.out.println( "Extracting jfr.jar" );
//        ProcessExecutor.run( "jar", Arrays.asList( "jar", "-xf", jfrJar.toString() ), java8Dir );

        System.out.println( "Creating java8-api.jar" );

        jar.get().run(
                System.out,
                System.err,
                "-cf", extractedClassesDir.getParent().resolve( "java8-api.jar" ).toString(),
                "-C", java8Dir.toString(),
                "."
        );
    }

    private static void mergeJava9Api(Path java9Home, Path extractedClassesDir) throws IOException {
        System.out.println( "Merging Java 9 modules" );

        Path java9Dir = extractedClassesDir.resolve( "java9" );

        Optional<ToolProvider> jmod = ToolProvider.findFirst( "jmod" );
        if ( !jmod.isPresent() ) {
            throw new IllegalStateException( "Couldn't find jmod tool" );
        }

        Optional<ToolProvider> jar = ToolProvider.findFirst( "jar" );
        if ( !jar.isPresent() ) {
            throw new IllegalStateException( "Couldn't find jar tool" );
        }

        Files.list( java9Home.resolve( "jmods" ) )
            .filter( p -> !p.getFileName().toString().startsWith( "jdk.internal") )
            .forEach( module -> {
                System.out.println( "Extracting module " + module );
                jmod.get().run( System.out, System.err, "extract", "--dir", java9Dir.toString(), module.toString() );
            });

        Files.delete( java9Dir.resolve( "classes" ).resolve( "module-info.class") );

        System.out.println( "Creating java9-api.jar" );

        jar.get().run(
                System.out,
                System.err,
                "-cf", extractedClassesDir.getParent().resolve( "java9-api.jar" ).toString(),
                "-C", java9Dir.resolve( "classes" ).toString(),
                "."
        );
    }


    private static Path delete(Path dir) {
        try {
            if ( Files.exists( dir ) ) {
                Files.walkFileTree( dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete( file );
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete( dir );
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            Files.createDirectory( dir );
        }
        catch (IOException e) {
            throw new RuntimeException( "Couldn't recreate directory " + dir, e );
        }

        return dir;
    }
}
