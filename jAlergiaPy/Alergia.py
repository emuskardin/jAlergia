import os

from aalpy.utils import load_automaton_from_file


def run_JAlergia(path_to_file, automaton_type, eps=0.005, heap_memory='-Xmx2048M'):
    assert automaton_type in {'mdp', 'smm', 'mc'}
    """
    Run Alergia or IOAlergia on provided data.

    Args:

        data: path to file containin fata either in a form [[I,I,I],[I,I,I],...] if learning Markov Chains or
        [[O,(I,O),(I,O)...],
        [O,(I,O_,...],..,] if learning MDPs (I represents input, O output).
        Note that in whole data first symbol of each entry should be the same (Initial output of the MDP/MC).

        eps: epsilon value

        automaton_type: either 'mdp' if you wish to learn an MDP, 'mc' if you want to learn Markov Chain,
         or 'smm' if you
                        want to learn stochastic Mealy machine


    Returns:

        learnedModel
    """

    save_file = "jAlergiaModel.dot"
    if os.path.exists(save_file):
        os.remove(save_file)

    if os.path.exists(path_to_file):
        abs_path = os.path.abspath(path_to_file)
    else:
        print('Input file not found.')
        return

    import subprocess
    subprocess.call(['java', heap_memory, '-jar', 'alergia.jar', '-path', abs_path, '-eps', str(eps), '-type', automaton_type])

    if not os.path.exists(save_file):
        print("Alergia error occurred.")
        return

    return load_automaton_from_file(save_file, automaton_type=automaton_type)
