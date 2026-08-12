Vagrant.configure("2") do |config|
  config.vm.box = "cloud-image/ubuntu-24.04"
  config.vm.box_version = "20260801.0.0"
  config.vm.hostname = "pegelconnect-pro"
  config.vm.boot_timeout = 600

  # Shared Folder bewusst deaktiviert
  config.vm.synced_folder ".", "/vagrant", disabled: true

  # Direkter Java-Backend-Zugriff
  config.vm.network "forwarded_port",
    guest: 8080,
    host: 8080,
    host_ip: "127.0.0.1",
    auto_correct: false

  # MQTT vom Windows-Host
  config.vm.network "forwarded_port",
    guest: 1883,
    host: 1885,
    host_ip: "127.0.0.1",
    auto_correct: false

  # NGINX Webserver
  config.vm.network "forwarded_port",
    guest: 80,
    host: 8088,
    host_ip: "127.0.0.1",
    auto_correct: false

  config.vm.provider "virtualbox" do |vb|
    vb.name = "PegelConnect-Pro"
    vb.memory = 2048
    vb.cpus = 2
    vb.gui = false

    vb.customize ["modifyvm", :id, "--ioapic", "on"]
    vb.check_guest_additions = false
  end

  # Maven-Konfiguration in die VM kopieren
  config.vm.provision "file",
    source: "pom.xml",
    destination: "/tmp/pegelconnect-pro/pom.xml"

  # Java + Frontend Sources in die VM kopieren
  config.vm.provision "file",
    source: "src",
    destination: "/tmp/pegelconnect-pro/src"

  # Provisioning-Skript in die VM kopieren
  config.vm.provision "file",
    source: "infrastructure/provision.sh",
    destination: "/tmp/pegelconnect-pro/provision.sh"

  # Provisioning ausführen
  config.vm.provision "shell",
    inline: <<-SHELL
      chmod +x /tmp/pegelconnect-pro/provision.sh
      /tmp/pegelconnect-pro/provision.sh
    SHELL
end