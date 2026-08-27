# ProGuard/R8 rules for the release build.
#
# Referenced by app/build.gradle.kts. Minification is currently off, so nothing here is
# read yet — but the file has to exist before it can be turned on, and turning it on is
# what you want before publishing.

# Keep line numbers so a stack trace from a released build can still be read, and hide
# the source file name that would otherwise be the only thing left of it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The app reflects over nothing and its only network use is org.json parsing of a response
# it reads field by field, so no keep rules are needed for the model classes.
