# Script de "power-on" logiciel du module GSM.
#
# Historique : sur l'ancienne HAT SIM800 (2G), le PWRKEY du module devait être
# tiré à la masse brièvement via GPIO4 (pin BOARD 7) pour l'allumer -- c'est ce
# que fait le code ci-dessous.
#
# Sur la HAT SIM7600E-H (4G) actuelle, ce script n'est PAS nécessaire : par
# défaut un cavalier relie PWR à 3V3 et le module s'allume tout seul dès qu'il
# est alimenté, sans intervention GPIO. Le code est donc commenté pour
# l'instant (11/09/2026) -- il ne fait rien s'il est appelé en fallback par
# InterphoneApplication (cf. SerialPower.callPowerScript()).
#
# Si un jour un contrôle logiciel du démarrage est nécessaire, il faudra :
#   1. Déplacer le cavalier de la HAT sur PWR-D6.
#   2. Décommenter le code ci-dessous EN REMPLAÇANT le pin 7 (GPIO4, ancien
#      PWRKEY SIM800) par le bon pin BOARD pour GPIO6 (PWR-D6 du SIM7600E-H).

# import RPi.GPIO as GPIO
# import time
# GPIO.setmode(GPIO.BOARD)
# GPIO.setup(7, GPIO.OUT)
# while True:
# 	GPIO.output(7, GPIO.LOW)
# 	time.sleep(4)
# 	GPIO.output(7, GPIO.HIGH)
# 	break
# GPIO.cleanup()
