import com.diffplug.gradle.spotless.SpotlessExtension
import groovy.json.JsonSlurper
import org.cyclonedx.gradle.CycloneDxTask
import org.cyclonedx.model.AttachmentText
import org.cyclonedx.model.License
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.organization.PostalAddress

val frameworkVersions: Map<String, String> by extra
val memorySettings: Map<String, String> by extra

plugins {
    id("maven-publish")
    id("org.cyclonedx.bom") version "2.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.cyclonedx.bom")
    apply(plugin = "com.diffplug.spotless")

    version = if (version != "unspecified") version else "1.0.0"

    extensions.configure<SpotlessExtension> {
        java {
            palantirJavaFormat().formatJavadoc(true)
            // support for spotless:off and spotless:on comments
            toggleOffOn()
            licenseHeader("/* Copyright (C) \$YEAR OpenCQRS and contributors */")
        }
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Zugriff auf sourceSets über die JavaPluginExtension
    val javaExt = extensions.getByType<JavaPluginExtension>()

    // Task für sources.jar hinzufügen
    val sourcesJar = tasks.register<Jar>("sourcesJar") {
        from(javaExt.sourceSets.getByName("main").allSource)
        archiveClassifier.set("sources")
    }

    tasks.register<CycloneDxTask>("generateBom") {
        mustRunAfter(tasks.named("processResources"))

        // TODO: check if sufficient
        includeConfigs = setOf("runtimeClasspath", "testCompileClasspath")
        skipConfigs = setOf()
        outputFormat = "json"

        projectType = "library"

        setOrganizationalEntity {
            it.name = "Digital Frontiers GmbH & Co. KG"
            it.urls = listOf("https://www.digitalfrontiers.de")
            it.address = PostalAddress().apply {
                country = "Germany"
                locality = "71034 Böblingen"
                streetAddress = "Veilchenstraße 9"
            }
            it.contacts = listOf(
                OrganizationalContact().apply {
                    name = "Digital Frontiers GmbH & Co. KG"
                    email = "cqrs@digitalfrontiers.de"
                }
            )
        }

        setLicenseChoice {
            it.addLicense(
                License().apply {
                    name = "Apache-2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    setLicenseText(AttachmentText().apply { text = "QXBhY2hlIExpY2Vuc2UKVmVyc2lvbiAyLjAsIEphbnVhcnkgMjAwNApodHRwOi8vd3d3LmFwYWNoZS5vcmcvbGljZW5zZXMvCgpURVJNUyBBTkQgQ09ORElUSU9OUyBGT1IgVVNFLCBSRVBST0RVQ1RJT04sIEFORCBESVNUUklCVVRJT04KCjEuIERlZmluaXRpb25zLgoKIkxpY2Vuc2UiIHNoYWxsIG1lYW4gdGhlIHRlcm1zIGFuZCBjb25kaXRpb25zIGZvciB1c2UsIHJlcHJvZHVjdGlvbiwgYW5kIGRpc3RyaWJ1dGlvbiBhcyBkZWZpbmVkIGJ5IFNlY3Rpb25zIDEgdGhyb3VnaCA5IG9mIHRoaXMgZG9jdW1lbnQuCgoiTGljZW5zb3IiIHNoYWxsIG1lYW4gdGhlIGNvcHlyaWdodCBvd25lciBvciBlbnRpdHkgYXV0aG9yaXplZCBieSB0aGUgY29weXJpZ2h0IG93bmVyIHRoYXQgaXMgZ3JhbnRpbmcgdGhlIExpY2Vuc2UuCgoiTGVnYWwgRW50aXR5IiBzaGFsbCBtZWFuIHRoZSB1bmlvbiBvZiB0aGUgYWN0aW5nIGVudGl0eSBhbmQgYWxsIG90aGVyIGVudGl0aWVzIHRoYXQgY29udHJvbCwgYXJlIGNvbnRyb2xsZWQgYnksIG9yIGFyZSB1bmRlciBjb21tb24gY29udHJvbCB3aXRoIHRoYXQgZW50aXR5LiBGb3IgdGhlIHB1cnBvc2VzIG9mIHRoaXMgZGVmaW5pdGlvbiwgImNvbnRyb2wiIG1lYW5zIChpKSB0aGUgcG93ZXIsIGRpcmVjdCBvciBpbmRpcmVjdCwgdG8gY2F1c2UgdGhlIGRpcmVjdGlvbiBvciBtYW5hZ2VtZW50IG9mIHN1Y2ggZW50aXR5LCB3aGV0aGVyIGJ5IGNvbnRyYWN0IG9yIG90aGVyd2lzZSwgb3IgKGlpKSBvd25lcnNoaXAgb2YgZmlmdHkgcGVyY2VudCAoNTAlKSBvciBtb3JlIG9mIHRoZSBvdXRzdGFuZGluZyBzaGFyZXMsIG9yIChpaWkpIGJlbmVmaWNpYWwgb3duZXJzaGlwIG9mIHN1Y2ggZW50aXR5LgoKIllvdSIgKG9yICJZb3VyIikgc2hhbGwgbWVhbiBhbiBpbmRpdmlkdWFsIG9yIExlZ2FsIEVudGl0eSBleGVyY2lzaW5nIHBlcm1pc3Npb25zIGdyYW50ZWQgYnkgdGhpcyBMaWNlbnNlLgoKIlNvdXJjZSIgZm9ybSBzaGFsbCBtZWFuIHRoZSBwcmVmZXJyZWQgZm9ybSBmb3IgbWFraW5nIG1vZGlmaWNhdGlvbnMsIGluY2x1ZGluZyBidXQgbm90IGxpbWl0ZWQgdG8gc29mdHdhcmUgc291cmNlIGNvZGUsIGRvY3VtZW50YXRpb24gc291cmNlLCBhbmQgY29uZmlndXJhdGlvbiBmaWxlcy4KCiJPYmplY3QiIGZvcm0gc2hhbGwgbWVhbiBhbnkgZm9ybSByZXN1bHRpbmcgZnJvbSBtZWNoYW5pY2FsIHRyYW5zZm9ybWF0aW9uIG9yIHRyYW5zbGF0aW9uIG9mIGEgU291cmNlIGZvcm0sIGluY2x1ZGluZyBidXQgbm90IGxpbWl0ZWQgdG8gY29tcGlsZWQgb2JqZWN0IGNvZGUsIGdlbmVyYXRlZCBkb2N1bWVudGF0aW9uLCBhbmQgY29udmVyc2lvbnMgdG8gb3RoZXIgbWVkaWEgdHlwZXMuCgoiV29yayIgc2hhbGwgbWVhbiB0aGUgd29yayBvZiBhdXRob3JzaGlwLCB3aGV0aGVyIGluIFNvdXJjZSBvciBPYmplY3QgZm9ybSwgbWFkZSBhdmFpbGFibGUgdW5kZXIgdGhlIExpY2Vuc2UsIGFzIGluZGljYXRlZCBieSBhIGNvcHlyaWdodCBub3RpY2UgdGhhdCBpcyBpbmNsdWRlZCBpbiBvciBhdHRhY2hlZCB0byB0aGUgd29yayAoYW4gZXhhbXBsZSBpcyBwcm92aWRlZCBpbiB0aGUgQXBwZW5kaXggYmVsb3cpLgoKIkRlcml2YXRpdmUgV29ya3MiIHNoYWxsIG1lYW4gYW55IHdvcmssIHdoZXRoZXIgaW4gU291cmNlIG9yIE9iamVjdCBmb3JtLCB0aGF0IGlzIGJhc2VkIG9uIChvciBkZXJpdmVkIGZyb20pIHRoZSBXb3JrIGFuZCBmb3Igd2hpY2ggdGhlIGVkaXRvcmlhbCByZXZpc2lvbnMsIGFubm90YXRpb25zLCBlbGFib3JhdGlvbnMsIG9yIG90aGVyIG1vZGlmaWNhdGlvbnMgcmVwcmVzZW50LCBhcyBhIHdob2xlLCBhbiBvcmlnaW5hbCB3b3JrIG9mIGF1dGhvcnNoaXAuIEZvciB0aGUgcHVycG9zZXMgb2YgdGhpcyBMaWNlbnNlLCBEZXJpdmF0aXZlIFdvcmtzIHNoYWxsIG5vdCBpbmNsdWRlIHdvcmtzIHRoYXQgcmVtYWluIHNlcGFyYWJsZSBmcm9tLCBvciBtZXJlbHkgbGluayAob3IgYmluZCBieSBuYW1lKSB0byB0aGUgaW50ZXJmYWNlcyBvZiwgdGhlIFdvcmsgYW5kIERlcml2YXRpdmUgV29ya3MgdGhlcmVvZi4KCiJDb250cmlidXRpb24iIHNoYWxsIG1lYW4gYW55IHdvcmsgb2YgYXV0aG9yc2hpcCwgaW5jbHVkaW5nIHRoZSBvcmlnaW5hbCB2ZXJzaW9uIG9mIHRoZSBXb3JrIGFuZCBhbnkgbW9kaWZpY2F0aW9ucyBvciBhZGRpdGlvbnMgdG8gdGhhdCBXb3JrIG9yIERlcml2YXRpdmUgV29ya3MgdGhlcmVvZiwgdGhhdCBpcyBpbnRlbnRpb25hbGx5IHN1Ym1pdHRlZCB0byBMaWNlbnNvciBmb3IgaW5jbHVzaW9uIGluIHRoZSBXb3JrIGJ5IHRoZSBjb3B5cmlnaHQgb3duZXIgb3IgYnkgYW4gaW5kaXZpZHVhbCBvciBMZWdhbCBFbnRpdHkgYXV0aG9yaXplZCB0byBzdWJtaXQgb24gYmVoYWxmIG9mIHRoZSBjb3B5cmlnaHQgb3duZXIuIEZvciB0aGUgcHVycG9zZXMgb2YgdGhpcyBkZWZpbml0aW9uLCAic3VibWl0dGVkIiBtZWFucyBhbnkgZm9ybSBvZiBlbGVjdHJvbmljLCB2ZXJiYWwsIG9yIHdyaXR0ZW4gY29tbXVuaWNhdGlvbiBzZW50IHRvIHRoZSBMaWNlbnNvciBvciBpdHMgcmVwcmVzZW50YXRpdmVzLCBpbmNsdWRpbmcgYnV0IG5vdCBsaW1pdGVkIHRvIGNvbW11bmljYXRpb24gb24gZWxlY3Ryb25pYyBtYWlsaW5nIGxpc3RzLCBzb3VyY2UgY29kZSBjb250cm9sIHN5c3RlbXMsIGFuZCBpc3N1ZSB0cmFja2luZyBzeXN0ZW1zIHRoYXQgYXJlIG1hbmFnZWQgYnksIG9yIG9uIGJlaGFsZiBvZiwgdGhlIExpY2Vuc29yIGZvciB0aGUgcHVycG9zZSBvZiBkaXNjdXNzaW5nIGFuZCBpbXByb3ZpbmcgdGhlIFdvcmssIGJ1dCBleGNsdWRpbmcgY29tbXVuaWNhdGlvbiB0aGF0IGlzIGNvbnNwaWN1b3VzbHkgbWFya2VkIG9yIG90aGVyd2lzZSBkZXNpZ25hdGVkIGluIHdyaXRpbmcgYnkgdGhlIGNvcHlyaWdodCBvd25lciBhcyAiTm90IGEgQ29udHJpYnV0aW9uLiIKCiJDb250cmlidXRvciIgc2hhbGwgbWVhbiBMaWNlbnNvciBhbmQgYW55IGluZGl2aWR1YWwgb3IgTGVnYWwgRW50aXR5IG9uIGJlaGFsZiBvZiB3aG9tIGEgQ29udHJpYnV0aW9uIGhhcyBiZWVuIHJlY2VpdmVkIGJ5IExpY2Vuc29yIGFuZCBzdWJzZXF1ZW50bHkgaW5jb3Jwb3JhdGVkIHdpdGhpbiB0aGUgV29yay4KCjIuIEdyYW50IG9mIENvcHlyaWdodCBMaWNlbnNlLiBTdWJqZWN0IHRvIHRoZSB0ZXJtcyBhbmQgY29uZGl0aW9ucyBvZiB0aGlzIExpY2Vuc2UsIGVhY2ggQ29udHJpYnV0b3IgaGVyZWJ5IGdyYW50cyB0byBZb3UgYSBwZXJwZXR1YWwsIHdvcmxkd2lkZSwgbm9uLWV4Y2x1c2l2ZSwgbm8tY2hhcmdlLCByb3lhbHR5LWZyZWUsIGlycmV2b2NhYmxlIGNvcHlyaWdodCBsaWNlbnNlIHRvIHJlcHJvZHVjZSwgcHJlcGFyZSBEZXJpdmF0aXZlIFdvcmtzIG9mLCBwdWJsaWNseSBkaXNwbGF5LCBwdWJsaWNseSBwZXJmb3JtLCBzdWJsaWNlbnNlLCBhbmQgZGlzdHJpYnV0ZSB0aGUgV29yayBhbmQgc3VjaCBEZXJpdmF0aXZlIFdvcmtzIGluIFNvdXJjZSBvciBPYmplY3QgZm9ybS4KCjMuIEdyYW50IG9mIFBhdGVudCBMaWNlbnNlLiBTdWJqZWN0IHRvIHRoZSB0ZXJtcyBhbmQgY29uZGl0aW9ucyBvZiB0aGlzIExpY2Vuc2UsIGVhY2ggQ29udHJpYnV0b3IgaGVyZWJ5IGdyYW50cyB0byBZb3UgYSBwZXJwZXR1YWwsIHdvcmxkd2lkZSwgbm9uLWV4Y2x1c2l2ZSwgbm8tY2hhcmdlLCByb3lhbHR5LWZyZWUsIGlycmV2b2NhYmxlIChleGNlcHQgYXMgc3RhdGVkIGluIHRoaXMgc2VjdGlvbikgcGF0ZW50IGxpY2Vuc2UgdG8gbWFrZSwgaGF2ZSBtYWRlLCB1c2UsIG9mZmVyIHRvIHNlbGwsIHNlbGwsIGltcG9ydCwgYW5kIG90aGVyd2lzZSB0cmFuc2ZlciB0aGUgV29yaywgd2hlcmUgc3VjaCBsaWNlbnNlIGFwcGxpZXMgb25seSB0byB0aG9zZSBwYXRlbnQgY2xhaW1zIGxpY2Vuc2FibGUgYnkgc3VjaCBDb250cmlidXRvciB0aGF0IGFyZSBuZWNlc3NhcmlseSBpbmZyaW5nZWQgYnkgdGhlaXIgQ29udHJpYnV0aW9uKHMpIGFsb25lIG9yIGJ5IGNvbWJpbmF0aW9uIG9mIHRoZWlyIENvbnRyaWJ1dGlvbihzKSB3aXRoIHRoZSBXb3JrIHRvIHdoaWNoIHN1Y2ggQ29udHJpYnV0aW9uKHMpIHdhcyBzdWJtaXR0ZWQuIElmIFlvdSBpbnN0aXR1dGUgcGF0ZW50IGxpdGlnYXRpb24gYWdhaW5zdCBhbnkgZW50aXR5IChpbmNsdWRpbmcgYSBjcm9zcy1jbGFpbSBvciBjb3VudGVyY2xhaW0gaW4gYSBsYXdzdWl0KSBhbGxlZ2luZyB0aGF0IHRoZSBXb3JrIG9yIGEgQ29udHJpYnV0aW9uIGluY29ycG9yYXRlZCB3aXRoaW4gdGhlIFdvcmsgY29uc3RpdHV0ZXMgZGlyZWN0IG9yIGNvbnRyaWJ1dG9yeSBwYXRlbnQgaW5mcmluZ2VtZW50LCB0aGVuIGFueSBwYXRlbnQgbGljZW5zZXMgZ3JhbnRlZCB0byBZb3UgdW5kZXIgdGhpcyBMaWNlbnNlIGZvciB0aGF0IFdvcmsgc2hhbGwgdGVybWluYXRlIGFzIG9mIHRoZSBkYXRlIHN1Y2ggbGl0aWdhdGlvbiBpcyBmaWxlZC4KCjQuIFJlZGlzdHJpYnV0aW9uLiBZb3UgbWF5IHJlcHJvZHVjZSBhbmQgZGlzdHJpYnV0ZSBjb3BpZXMgb2YgdGhlIFdvcmsgb3IgRGVyaXZhdGl2ZSBXb3JrcyB0aGVyZW9mIGluIGFueSBtZWRpdW0sIHdpdGggb3Igd2l0aG91dCBtb2RpZmljYXRpb25zLCBhbmQgaW4gU291cmNlIG9yIE9iamVjdCBmb3JtLCBwcm92aWRlZCB0aGF0IFlvdSBtZWV0IHRoZSBmb2xsb3dpbmcgY29uZGl0aW9uczoKCiAgICAgKGEpIFlvdSBtdXN0IGdpdmUgYW55IG90aGVyIHJlY2lwaWVudHMgb2YgdGhlIFdvcmsgb3IgRGVyaXZhdGl2ZSBXb3JrcyBhIGNvcHkgb2YgdGhpcyBMaWNlbnNlOyBhbmQKCiAgICAgKGIpIFlvdSBtdXN0IGNhdXNlIGFueSBtb2RpZmllZCBmaWxlcyB0byBjYXJyeSBwcm9taW5lbnQgbm90aWNlcyBzdGF0aW5nIHRoYXQgWW91IGNoYW5nZWQgdGhlIGZpbGVzOyBhbmQKCiAgICAgKGMpIFlvdSBtdXN0IHJldGFpbiwgaW4gdGhlIFNvdXJjZSBmb3JtIG9mIGFueSBEZXJpdmF0aXZlIFdvcmtzIHRoYXQgWW91IGRpc3RyaWJ1dGUsIGFsbCBjb3B5cmlnaHQsIHBhdGVudCwgdHJhZGVtYXJrLCBhbmQgYXR0cmlidXRpb24gbm90aWNlcyBmcm9tIHRoZSBTb3VyY2UgZm9ybSBvZiB0aGUgV29yaywgZXhjbHVkaW5nIHRob3NlIG5vdGljZXMgdGhhdCBkbyBub3QgcGVydGFpbiB0byBhbnkgcGFydCBvZiB0aGUgRGVyaXZhdGl2ZSBXb3JrczsgYW5kCgogICAgIChkKSBJZiB0aGUgV29yayBpbmNsdWRlcyBhICJOT1RJQ0UiIHRleHQgZmlsZSBhcyBwYXJ0IG9mIGl0cyBkaXN0cmlidXRpb24sIHRoZW4gYW55IERlcml2YXRpdmUgV29ya3MgdGhhdCBZb3UgZGlzdHJpYnV0ZSBtdXN0IGluY2x1ZGUgYSByZWFkYWJsZSBjb3B5IG9mIHRoZSBhdHRyaWJ1dGlvbiBub3RpY2VzIGNvbnRhaW5lZCB3aXRoaW4gc3VjaCBOT1RJQ0UgZmlsZSwgZXhjbHVkaW5nIHRob3NlIG5vdGljZXMgdGhhdCBkbyBub3QgcGVydGFpbiB0byBhbnkgcGFydCBvZiB0aGUgRGVyaXZhdGl2ZSBXb3JrcywgaW4gYXQgbGVhc3Qgb25lIG9mIHRoZSBmb2xsb3dpbmcgcGxhY2VzOiB3aXRoaW4gYSBOT1RJQ0UgdGV4dCBmaWxlIGRpc3RyaWJ1dGVkIGFzIHBhcnQgb2YgdGhlIERlcml2YXRpdmUgV29ya3M7IHdpdGhpbiB0aGUgU291cmNlIGZvcm0gb3IgZG9jdW1lbnRhdGlvbiwgaWYgcHJvdmlkZWQgYWxvbmcgd2l0aCB0aGUgRGVyaXZhdGl2ZSBXb3Jrczsgb3IsIHdpdGhpbiBhIGRpc3BsYXkgZ2VuZXJhdGVkIGJ5IHRoZSBEZXJpdmF0aXZlIFdvcmtzLCBpZiBhbmQgd2hlcmV2ZXIgc3VjaCB0aGlyZC1wYXJ0eSBub3RpY2VzIG5vcm1hbGx5IGFwcGVhci4gVGhlIGNvbnRlbnRzIG9mIHRoZSBOT1RJQ0UgZmlsZSBhcmUgZm9yIGluZm9ybWF0aW9uYWwgcHVycG9zZXMgb25seSBhbmQgZG8gbm90IG1vZGlmeSB0aGUgTGljZW5zZS4gWW91IG1heSBhZGQgWW91ciBvd24gYXR0cmlidXRpb24gbm90aWNlcyB3aXRoaW4gRGVyaXZhdGl2ZSBXb3JrcyB0aGF0IFlvdSBkaXN0cmlidXRlLCBhbG9uZ3NpZGUgb3IgYXMgYW4gYWRkZW5kdW0gdG8gdGhlIE5PVElDRSB0ZXh0IGZyb20gdGhlIFdvcmssIHByb3ZpZGVkIHRoYXQgc3VjaCBhZGRpdGlvbmFsIGF0dHJpYnV0aW9uIG5vdGljZXMgY2Fubm90IGJlIGNvbnN0cnVlZCBhcyBtb2RpZnlpbmcgdGhlIExpY2Vuc2UuCgogICAgIFlvdSBtYXkgYWRkIFlvdXIgb3duIGNvcHlyaWdodCBzdGF0ZW1lbnQgdG8gWW91ciBtb2RpZmljYXRpb25zIGFuZCBtYXkgcHJvdmlkZSBhZGRpdGlvbmFsIG9yIGRpZmZlcmVudCBsaWNlbnNlIHRlcm1zIGFuZCBjb25kaXRpb25zIGZvciB1c2UsIHJlcHJvZHVjdGlvbiwgb3IgZGlzdHJpYnV0aW9uIG9mIFlvdXIgbW9kaWZpY2F0aW9ucywgb3IgZm9yIGFueSBzdWNoIERlcml2YXRpdmUgV29ya3MgYXMgYSB3aG9sZSwgcHJvdmlkZWQgWW91ciB1c2UsIHJlcHJvZHVjdGlvbiwgYW5kIGRpc3RyaWJ1dGlvbiBvZiB0aGUgV29yayBvdGhlcndpc2UgY29tcGxpZXMgd2l0aCB0aGUgY29uZGl0aW9ucyBzdGF0ZWQgaW4gdGhpcyBMaWNlbnNlLgoKNS4gU3VibWlzc2lvbiBvZiBDb250cmlidXRpb25zLiBVbmxlc3MgWW91IGV4cGxpY2l0bHkgc3RhdGUgb3RoZXJ3aXNlLCBhbnkgQ29udHJpYnV0aW9uIGludGVudGlvbmFsbHkgc3VibWl0dGVkIGZvciBpbmNsdXNpb24gaW4gdGhlIFdvcmsgYnkgWW91IHRvIHRoZSBMaWNlbnNvciBzaGFsbCBiZSB1bmRlciB0aGUgdGVybXMgYW5kIGNvbmRpdGlvbnMgb2YgdGhpcyBMaWNlbnNlLCB3aXRob3V0IGFueSBhZGRpdGlvbmFsIHRlcm1zIG9yIGNvbmRpdGlvbnMuIE5vdHdpdGhzdGFuZGluZyB0aGUgYWJvdmUsIG5vdGhpbmcgaGVyZWluIHNoYWxsIHN1cGVyc2VkZSBvciBtb2RpZnkgdGhlIHRlcm1zIG9mIGFueSBzZXBhcmF0ZSBsaWNlbnNlIGFncmVlbWVudCB5b3UgbWF5IGhhdmUgZXhlY3V0ZWQgd2l0aCBMaWNlbnNvciByZWdhcmRpbmcgc3VjaCBDb250cmlidXRpb25zLgoKNi4gVHJhZGVtYXJrcy4gVGhpcyBMaWNlbnNlIGRvZXMgbm90IGdyYW50IHBlcm1pc3Npb24gdG8gdXNlIHRoZSB0cmFkZSBuYW1lcywgdHJhZGVtYXJrcywgc2VydmljZSBtYXJrcywgb3IgcHJvZHVjdCBuYW1lcyBvZiB0aGUgTGljZW5zb3IsIGV4Y2VwdCBhcyByZXF1aXJlZCBmb3IgcmVhc29uYWJsZSBhbmQgY3VzdG9tYXJ5IHVzZSBpbiBkZXNjcmliaW5nIHRoZSBvcmlnaW4gb2YgdGhlIFdvcmsgYW5kIHJlcHJvZHVjaW5nIHRoZSBjb250ZW50IG9mIHRoZSBOT1RJQ0UgZmlsZS4KCjcuIERpc2NsYWltZXIgb2YgV2FycmFudHkuIFVubGVzcyByZXF1aXJlZCBieSBhcHBsaWNhYmxlIGxhdyBvciBhZ3JlZWQgdG8gaW4gd3JpdGluZywgTGljZW5zb3IgcHJvdmlkZXMgdGhlIFdvcmsgKGFuZCBlYWNoIENvbnRyaWJ1dG9yIHByb3ZpZGVzIGl0cyBDb250cmlidXRpb25zKSBvbiBhbiAiQVMgSVMiIEJBU0lTLCBXSVRIT1VUIFdBUlJBTlRJRVMgT1IgQ09ORElUSU9OUyBPRiBBTlkgS0lORCwgZWl0aGVyIGV4cHJlc3Mgb3IgaW1wbGllZCwgaW5jbHVkaW5nLCB3aXRob3V0IGxpbWl0YXRpb24sIGFueSB3YXJyYW50aWVzIG9yIGNvbmRpdGlvbnMgb2YgVElUTEUsIE5PTi1JTkZSSU5HRU1FTlQsIE1FUkNIQU5UQUJJTElUWSwgb3IgRklUTkVTUyBGT1IgQSBQQVJUSUNVTEFSIFBVUlBPU0UuIFlvdSBhcmUgc29sZWx5IHJlc3BvbnNpYmxlIGZvciBkZXRlcm1pbmluZyB0aGUgYXBwcm9wcmlhdGVuZXNzIG9mIHVzaW5nIG9yIHJlZGlzdHJpYnV0aW5nIHRoZSBXb3JrIGFuZCBhc3N1bWUgYW55IHJpc2tzIGFzc29jaWF0ZWQgd2l0aCBZb3VyIGV4ZXJjaXNlIG9mIHBlcm1pc3Npb25zIHVuZGVyIHRoaXMgTGljZW5zZS4KCjguIExpbWl0YXRpb24gb2YgTGlhYmlsaXR5LiBJbiBubyBldmVudCBhbmQgdW5kZXIgbm8gbGVnYWwgdGhlb3J5LCB3aGV0aGVyIGluIHRvcnQgKGluY2x1ZGluZyBuZWdsaWdlbmNlKSwgY29udHJhY3QsIG9yIG90aGVyd2lzZSwgdW5sZXNzIHJlcXVpcmVkIGJ5IGFwcGxpY2FibGUgbGF3IChzdWNoIGFzIGRlbGliZXJhdGUgYW5kIGdyb3NzbHkgbmVnbGlnZW50IGFjdHMpIG9yIGFncmVlZCB0byBpbiB3cml0aW5nLCBzaGFsbCBhbnkgQ29udHJpYnV0b3IgYmUgbGlhYmxlIHRvIFlvdSBmb3IgZGFtYWdlcywgaW5jbHVkaW5nIGFueSBkaXJlY3QsIGluZGlyZWN0LCBzcGVjaWFsLCBpbmNpZGVudGFsLCBvciBjb25zZXF1ZW50aWFsIGRhbWFnZXMgb2YgYW55IGNoYXJhY3RlciBhcmlzaW5nIGFzIGEgcmVzdWx0IG9mIHRoaXMgTGljZW5zZSBvciBvdXQgb2YgdGhlIHVzZSBvciBpbmFiaWxpdHkgdG8gdXNlIHRoZSBXb3JrIChpbmNsdWRpbmcgYnV0IG5vdCBsaW1pdGVkIHRvIGRhbWFnZXMgZm9yIGxvc3Mgb2YgZ29vZHdpbGwsIHdvcmsgc3RvcHBhZ2UsIGNvbXB1dGVyIGZhaWx1cmUgb3IgbWFsZnVuY3Rpb24sIG9yIGFueSBhbmQgYWxsIG90aGVyIGNvbW1lcmNpYWwgZGFtYWdlcyBvciBsb3NzZXMpLCBldmVuIGlmIHN1Y2ggQ29udHJpYnV0b3IgaGFzIGJlZW4gYWR2aXNlZCBvZiB0aGUgcG9zc2liaWxpdHkgb2Ygc3VjaCBkYW1hZ2VzLgoKOS4gQWNjZXB0aW5nIFdhcnJhbnR5IG9yIEFkZGl0aW9uYWwgTGlhYmlsaXR5LiBXaGlsZSByZWRpc3RyaWJ1dGluZyB0aGUgV29yayBvciBEZXJpdmF0aXZlIFdvcmtzIHRoZXJlb2YsIFlvdSBtYXkgY2hvb3NlIHRvIG9mZmVyLCBhbmQgY2hhcmdlIGEgZmVlIGZvciwgYWNjZXB0YW5jZSBvZiBzdXBwb3J0LCB3YXJyYW50eSwgaW5kZW1uaXR5LCBvciBvdGhlciBsaWFiaWxpdHkgb2JsaWdhdGlvbnMgYW5kL29yIHJpZ2h0cyBjb25zaXN0ZW50IHdpdGggdGhpcyBMaWNlbnNlLiBIb3dldmVyLCBpbiBhY2NlcHRpbmcgc3VjaCBvYmxpZ2F0aW9ucywgWW91IG1heSBhY3Qgb25seSBvbiBZb3VyIG93biBiZWhhbGYgYW5kIG9uIFlvdXIgc29sZSByZXNwb25zaWJpbGl0eSwgbm90IG9uIGJlaGFsZiBvZiBhbnkgb3RoZXIgQ29udHJpYnV0b3IsIGFuZCBvbmx5IGlmIFlvdSBhZ3JlZSB0byBpbmRlbW5pZnksIGRlZmVuZCwgYW5kIGhvbGQgZWFjaCBDb250cmlidXRvciBoYXJtbGVzcyBmb3IgYW55IGxpYWJpbGl0eSBpbmN1cnJlZCBieSwgb3IgY2xhaW1zIGFzc2VydGVkIGFnYWluc3QsIHN1Y2ggQ29udHJpYnV0b3IgYnkgcmVhc29uIG9mIHlvdXIgYWNjZXB0aW5nIGFueSBzdWNoIHdhcnJhbnR5IG9yIGFkZGl0aW9uYWwgbGlhYmlsaXR5LgoKRU5EIE9GIFRFUk1TIEFORCBDT05ESVRJT05TCgpBUFBFTkRJWDogSG93IHRvIGFwcGx5IHRoZSBBcGFjaGUgTGljZW5zZSB0byB5b3VyIHdvcmsuCgpUbyBhcHBseSB0aGUgQXBhY2hlIExpY2Vuc2UgdG8geW91ciB3b3JrLCBhdHRhY2ggdGhlIGZvbGxvd2luZyBib2lsZXJwbGF0ZSBub3RpY2UsIHdpdGggdGhlIGZpZWxkcyBlbmNsb3NlZCBieSBicmFja2V0cyAiW10iIHJlcGxhY2VkIHdpdGggeW91ciBvd24gaWRlbnRpZnlpbmcgaW5mb3JtYXRpb24uIChEb24ndCBpbmNsdWRlIHRoZSBicmFja2V0cyEpICBUaGUgdGV4dCBzaG91bGQgYmUgZW5jbG9zZWQgaW4gdGhlIGFwcHJvcHJpYXRlIGNvbW1lbnQgc3ludGF4IGZvciB0aGUgZmlsZSBmb3JtYXQuIFdlIGFsc28gcmVjb21tZW5kIHRoYXQgYSBmaWxlIG9yIGNsYXNzIG5hbWUgYW5kIGRlc2NyaXB0aW9uIG9mIHB1cnBvc2UgYmUgaW5jbHVkZWQgb24gdGhlIHNhbWUgInByaW50ZWQgcGFnZSIgYXMgdGhlIGNvcHlyaWdodCBub3RpY2UgZm9yIGVhc2llciBpZGVudGlmaWNhdGlvbiB3aXRoaW4gdGhpcmQtcGFydHkgYXJjaGl2ZXMuCgpDb3B5cmlnaHQgW3l5eXldIFtuYW1lIG9mIGNvcHlyaWdodCBvd25lcl0KCkxpY2Vuc2VkIHVuZGVyIHRoZSBBcGFjaGUgTGljZW5zZSwgVmVyc2lvbiAyLjAgKHRoZSAiTGljZW5zZSIpOwp5b3UgbWF5IG5vdCB1c2UgdGhpcyBmaWxlIGV4Y2VwdCBpbiBjb21wbGlhbmNlIHdpdGggdGhlIExpY2Vuc2UuCllvdSBtYXkgb2J0YWluIGEgY29weSBvZiB0aGUgTGljZW5zZSBhdAoKaHR0cDovL3d3dy5hcGFjaGUub3JnL2xpY2Vuc2VzL0xJQ0VOU0UtMi4wCgpVbmxlc3MgcmVxdWlyZWQgYnkgYXBwbGljYWJsZSBsYXcgb3IgYWdyZWVkIHRvIGluIHdyaXRpbmcsIHNvZnR3YXJlCmRpc3RyaWJ1dGVkIHVuZGVyIHRoZSBMaWNlbnNlIGlzIGRpc3RyaWJ1dGVkIG9uIGFuICJBUyBJUyIgQkFTSVMsCldJVEhPVVQgV0FSUkFOVElFUyBPUiBDT05ESVRJT05TIE9GIEFOWSBLSU5ELCBlaXRoZXIgZXhwcmVzcyBvciBpbXBsaWVkLgpTZWUgdGhlIExpY2Vuc2UgZm9yIHRoZSBzcGVjaWZpYyBsYW5ndWFnZSBnb3Zlcm5pbmcgcGVybWlzc2lvbnMgYW5kCmxpbWl0YXRpb25zIHVuZGVyIHRoZSBMaWNlbnNlLgo" })
                }
            )
        }
    }

    tasks.register("licenseCheck") {
        dependsOn("generateBom")

        doLast {
            // TODO: adapt & check (not alle licenses extracted have proper SPDX)
            val forbiddenLicenses = setOf(
                "GPL-3.0",
                "GPL-2.0",
                "AGPL-3.0",
                "EUPL-1.2",
            )
            val bomFile = file("build/reports/bom.json")

            if (!bomFile.exists()) {
                throw GradleException("bom not found: ${bomFile.absolutePath}")
            }

            val json = JsonSlurper().parse(bomFile) as Map<*, *>
            val components = json["components"] as? List<Map<String, Any>> ?: emptyList()

            val violations = components.mapNotNull { component ->
                val licenses = (component["licenses"] as? List<Map<String, Map<String, Any>>>)?.mapNotNull { it["license"]?.get("id") as? String } ?: emptyList()
                val badLicenses = licenses.filter { it in forbiddenLicenses }
                if (badLicenses.isNotEmpty()) "${component["name"]}: $badLicenses" else null
            }

            if (violations.isNotEmpty()) {
                throw GradleException("forbidden licenses found for:\n${violations.joinToString("\n")}")
            }
        }
    }

    afterEvaluate {
        dependencyManagement {
            imports {
                mavenBom("org.springframework.boot:spring-boot-dependencies:${frameworkVersions.get("spring.boot.version")}")
            }
        }

        tasks.named<Jar>("jar") {
            dependsOn("licenseCheck")

            from("build/reports") {
                include("bom.json")
                rename { "library.cdx.json" }
                into("META-INF/sbom")
            }

            manifest {
                attributes["Sbom-Location"] = "META-INF/sbom/library.cdx.json"
                attributes["Sbom-Format"] = "CycloneDX"
            }
        }

        tasks.named<Test>("test") {
            useJUnitPlatform()
            systemProperty("esdb.version", frameworkVersions.getValue("esdb.version"))

            minHeapSize = memorySettings.getValue("test.min-heap-size")
            maxHeapSize = memorySettings.getValue("test.max-heap-size")
        }

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])

                    artifact(sourcesJar.get())

                    groupId = "com.opencqrs"
                    artifactId = project.name

                    versionMapping {
                        usage("java-api") {
                            fromResolutionOf("runtimeClasspath")
                        }
                        usage("java-runtime") {
                            fromResolutionResult()
                        }
                    }
                }
            }
        }
    }
}
