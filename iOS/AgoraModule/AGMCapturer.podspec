Pod::Spec.new do |spec|
  spec.name         = "AGMCapturer"
  spec.version      = "1.0.0"
  spec.summary      = "ZhaoYongqiang"
  spec.description  = "Video Custom Capturer"

  spec.homepage     = "https://github.com/AgoraIO-Community/MediaIO-Camera"
  spec.license      = "MIT"
  spec.author       = { "ZYQ" => "zhaoyongqiang@agora.io" }
  spec.source       = { :git => "https://github.com/AgoraIO-Community/MediaIO-Camera.git" }
  spec.vendored_frameworks = "AGMFramework/*.framework"
  spec.pod_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64', 'DEFINES_MODULE' => 'YES' }
  spec.user_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64', 'DEFINES_MODULE' => 'YES' }
  spec.ios.deployment_target = '10.0'
  spec.requires_arc  = true
  spec.static_framework = true
end
