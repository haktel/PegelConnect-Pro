Vagrant.configure("2") do |config|

  config.vm.box = "cloud-image/ubuntu-24.04"
  config.vm.box_version = "20260801.0.0"

  config.vm.hostname = "pegelconnect-pro"
  config.vm.boot_timeout = 600


  # ============================================================
  # SHARED FOLDER
  # ============================================================

  # Bewusst deaktiviert.
  # Deployment erfolgt reproduzierbar über Vagrant File Provisioner.

  config.vm.synced_folder ".", "/vagrant", disabled: true


  # ============================================================
  # NETWORK / PORT FORWARDING
  # ============================================================

  # Direkter Java Backend Zugriff
  config.vm.network "forwarded_port",
    guest: 8080,
    host: 8080,
    host_ip: "127.0.0.1",
    auto_correct: false


  # MQTT Zugriff vom Windows Host
  config.vm.network "forwarded_port",
    guest: 1883,
    host: 1885,
    host_ip: "127.0.0.1",
    auto_correct: false


  # NGINX Reverse Proxy / Dashboard
  config.vm.network "forwarded_port",
    guest: 80,
    host: 8088,
    host_ip: "127.0.0.1",
    auto_correct: false


  # ============================================================
  # VIRTUALBOX
  # ============================================================

  config.vm.provider "virtualbox" do |vb|

    vb.name = "PegelConnect-Pro"

    vb.memory = 2048
    vb.cpus = 2

    vb.gui = false

    vb.customize [
      "modifyvm",
      :id,
      "--ioapic",
      "on"
    ]

    vb.check_guest_additions = false
  end


  # ============================================================
  # DEPLOYMENT
  # ============================================================

  # Maven Projektdatei
  config.vm.provision "file",
    source: "pom.xml",
    destination: "/tmp/pegelconnect-pro/pom.xml"


  # Java + Frontend Sources
  config.vm.provision "file",
    source: "src",
    destination: "/tmp/pegelconnect-pro/src"


  # ============================================================
  # EXTERNAL CONFIGURATION
  # ============================================================

  # Zentrale Anwendungskonfiguration.
  #
  # Diese Datei wird später durch provision.sh nach
  # /etc/pegelconnect-pro/config.json installiert.

  config.vm.provision "file",
    source: "config/config.json",
    destination: "/tmp/pegelconnect-pro/config.json"


  # ============================================================
  # PROVISIONING SCRIPT
  # ============================================================

  config.vm.provision "file",
    source: "infrastructure/provision.sh",
    destination: "/tmp/pegelconnect-pro/provision.sh"


  config.vm.provision "shell",
    inline: <<-SHELL

      chmod +x /tmp/pegelconnect-pro/provision.sh

      /tmp/pegelconnect-pro/provision.sh

    SHELL

end