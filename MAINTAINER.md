
# Publication to Maven Central

Make sure you have created and switched to an appropriate Git tag, before continuing!

## Environment

Your environment needs to provide the following settings, preferably using `../.envrc.local`:
```shell
# only upload to Central Sonatype, publication is done manually via Web-UI
export JRELEASER_MAVENCENTRAL_STAGE=UPLOAD
export JRELEASER_MAVENCENTRAL_USERNAME=<central sonatype deploy token username>
export JRELEASER_MAVENCENTRAL_PASSWORD=<central sonatype deploy token password>
export SIGNING_KEY=<base64 encoded armored PGP private key>
export SIGNING_PASSWORD<PGP key passphrase>
```

## Build & Upload

Build the project using the tagged version and upload it to Central Sonatype:
```
export VERSION_TAG=$(git describe --exact-match --tags 2> /dev/null || git rev-parse --short HEAD)
# verify it's the correct tag
echo $VERSION_TAG

./gradlew -Pversion=$VERSION_TAG clean build publishAllPublicationsToStagingRepository

jreleaser-cli deploy --output-directory build -Djreleaser.project.version=$VERSION_TAG
```

## Publish

After having been validated, you may manually publish the bundle via [Central Sonatype](https://central.sonatype.com/publishing).