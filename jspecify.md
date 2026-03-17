# Notes on Jspecify + Nullaway + Errorprone

## Defaults

- by default the setup doesn't specify nullness - [unspecified nullness](https://jspecify.dev/docs/user-guide/#types-and-nullness)
- we set every package to [`@NullMarked`](https://jspecify.dev/docs/user-guide/#nullmarked) in `package-info.java` 

So all Types that are NOT annotated with `@Nullable` are treated as never null (`@NonNull`) by default.

## OpenCQRS Null-Safety guarantees

OpenCQRS provides two levels of Null-safety, one is on by default, the other is opt-in

### Intellij

This requires no setup, as IntelliJ can read the Jspecify Nullness annotations from the Java Bytecode.
If you have IntelliJ hints on (default is on), the IDE will add visual hints when you're using a nullable value in a way
that can lead to NPEs. (unconfirmed:) Likewise, IntelliJ will show you when you're passing a nullable value to a method
that expects non-null values.

- JSpecify annotations, even when used with `compileOnly()`, are carried over to the bytecode.
- IntelliJ can read those annotations from bytecode, so no need to download src to have IntelliJ show the Nullness.

### Compile-time errors or warnings

Null-safety at compile-time is only possible with additional plug-ins and is therefore opt-in.

If you want your build to break when null-safety is being violated, you need to install and configure two plug-ins:
- NullAway
- ErrorProne

ErrorProne is a static code analysis tool with a myriad of rules and checks.  
NullAway is a plug-in for ErrorProne that brings null-safety checks to ErrorProne.  

**Important:** ErrorProne will perform all its checks by default. So if you're only interested in the Null-Safety aspect,
you'll have to configure it to discard all its other rules and only use NullAway.


```kotlin
plugins {
    id("net.ltgt.errorprone") version "3.1.0"
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.x.x")
    errorprone("com.uber.nullaway:nullaway:0.x.x")
}

tasks.withType(JavaCompile).configureEach {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:JSpecifyMode", "true")
    }
}
```

Jspecify NullMarked for big safety