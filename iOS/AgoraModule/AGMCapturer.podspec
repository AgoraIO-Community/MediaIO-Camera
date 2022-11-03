Pod::Spec.new do |spec|
  spec.name         = "AGMCapturer"
  spec.version      = "1.1.7.2"
  spec.summary      = "ZhaoYongqiang"
  spec.description  = "Video Custom Capturer"
  spec.homepage     = "https://github.com/AgoraIO-Community/MediaIO-Camera"
  spec.license      = "MIT"
  spec.author       = { "ZYQ" => "zhaoyongqiang@agora.io" }
  spec.source       = { :git => "https://github.com/AgoraIO-Community/MediaIO-Camera.git", :tag => spec.version }
  spec.vendored_frameworks = "iOS/AgoraModule/AGMFramework/*.framework"
  spec.pod_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64', 'DEFINES_MODULE' => 'YES' }
  spec.user_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64', 'DEFINES_MODULE' => 'YES' }
  spec.ios.deployment_target = '10.0'
  spec.requires_arc  = true
  spec.static_framework = true
end
