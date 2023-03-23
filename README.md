# Jenkins Build Notifications Plugin

---

**This plugin is now maintained at the Jenkins GitHub Organization**

https://github.com/jenkinsci/build-notifications-plugin

---

This is a plugin to enable build notifications through [Pushover][], [Telegram][] or even [Boteco][].

## How to build

Just execute a `mvn package -DskipTests` and upload the *target/hpi* package to your Jenkins instance.

#### Docker build example:
    docker run -it --rm --name notifications-plugin -v ${pwd}:/usr/src/notifications-plugin -w /usr/src/notifications-plugin maven:3.3-jdk-8 mvn package -DskipTests

## How to configure

There are global and specific options:

### Global options

Global options should be configured in Jenkins System Configuration. You'll need to set
an Application Token for Pushover and/or a Bot Token for Telegram.

### Specific options

There are per-job configurations. You need to add a post-build step (there is a separated
step for each notification service) and configure the target to receive notifications.

## How you'll be notified

Notifications will include:

- The project's name
- The build number
- The build result
- The build's changes
- The build link

If you are receiving notifications through Pushover, the notification will be sent with
high priority in case of a fail build, low priority in case of a success build and normal
priority for the other cases.

Note that Telegram doesn't have a way to set priority for messages (and is understandable
because Telegram is a chat platform and not a notification platform like Pushover).

## How to contribute

Open an issue, spread the project, use it, fork it...

[pushover]: <http://pushover.net/>
[telegram]: <https://telegram.org/>
[boteco]: <https://github.com/devnull-tools/boteco>
