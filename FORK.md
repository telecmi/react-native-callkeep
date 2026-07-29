# Why this fork exists

`@telecmi/react-native-callkeep` is [react-native-callkeep](https://github.com/react-native-webrtc/react-native-callkeep)
**4.3.16** with one fix: the Android module declared `displayIncomingCall` and
`startCall` twice with `@ReactMethod` (a 3-argument overload plus the full
signature). React Native 0.76+'s New Architecture rejects duplicate exported
names, crashing the app at startup with:

```
Unable to parse @ReactMethod annotations from native module: RNCallKeep.
Details: Module exports two methods to JavaScript with the same name: "displayIncomingCall"
```

The fix removes `@ReactMethod` from the two redundant overloads (the JS layer
only calls the full signatures). Everything else is byte-for-byte upstream;
the upstream ISC license applies unchanged.

This package ships as a dependency of
[`@telecmi/piopiy-native`](https://www.npmjs.com/package/@telecmi/piopiy-native)
so PIOPIY SDK apps install and patch nothing. It is not intended for
standalone use — prefer upstream once it carries the equivalent fix.
